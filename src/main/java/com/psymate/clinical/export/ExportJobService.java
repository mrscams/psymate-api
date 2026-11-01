package com.psymate.clinical.export;

import com.psymate.clinical.export.ExportJobRepository.ExportJobView;
import com.psymate.identity.api.EncryptedStorageFacade;
import com.psymate.shared.problem.ProblemException;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Export du compte refusé, suspendu ou actif (fiche 27, G Q18) : demande, suivi, téléchargement
 * signé et purge. La demande insère la ligne {@code export_job} (queued) dans une transaction qui
 * verrouille le praticien (un seul job queued ou running à la fois), puis, la transaction validée,
 * confie le travail à un pool privé : un seul fil, file bornée, fils daemon (pas de
 * {@code @EnableAsync}, pas de TaskExecutor global injecté, comme ContactRequestService).
 *
 * <p>Les URL sont signées par HMAC avec la clé des URL signées des justificatifs : le jeton de
 * suivi lie le job et son praticien, l'URL du fichier lie en plus l'échéance. Comparaison en temps
 * constant.
 */
@Service
class ExportJobService {

  /** Durée de vie du fichier après la fin du travail. */
  static final Duration RETENTION = Duration.ofDays(7);

  /** Un job queued ou running plus vieux que cela est considéré interrompu (arrêt du serveur). */
  static final Duration STALE_AFTER = Duration.ofHours(1);

  static final String STATUS_PREFIX = "export-status|";
  static final String FILE_PREFIX = "export-file|";

  private static final Logger LOG = LoggerFactory.getLogger(ExportJobService.class);

  /** Job créé : identifiant et URL de suivi (avec son jeton). */
  record Requested(UUID jobId, String statusUrl) {}

  /** Vue d'un job pour l'API ; {@code downloadUrl} seulement tant que le fichier est valable. */
  record JobStatus(
      UUID jobId,
      String status,
      Instant requestedAt,
      Instant finishedAt,
      Instant expiresAt,
      String downloadUrl,
      String error) {}

  /** Fichier prêt à servir. */
  record ZipFile(String filename, byte[] content) {}

  private final ExportJobRepository jobs;
  private final ExportJobRunner runner;
  private final EncryptedStorageFacade storage;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final ThreadPoolExecutor pool =
      new ThreadPoolExecutor(
          1,
          1,
          0L,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(50),
          r -> {
            Thread t = new Thread(r, "account-export");
            t.setDaemon(true);
            return t;
          });

  ExportJobService(
      ExportJobRepository jobs,
      ExportJobRunner runner,
      EncryptedStorageFacade storage,
      TransactionTemplate tx,
      Clock clock) {
    this.jobs = jobs;
    this.runner = runner;
    this.storage = storage;
    this.tx = tx;
    this.clock = clock;
  }

  @PreDestroy
  void stop() {
    pool.shutdown();
  }

  // ---- demande

  /**
   * Crée le job et le soumet après validation de la transaction.
   *
   * @throws ProblemException 409 EXPORT_IN_PROGRESS si un job est déjà queued ou running
   */
  Requested request(UUID practitionerId, ExportTexts texts) {
    UUID id = UUID.randomUUID();
    Instant now = clock.instant();
    tx.executeWithoutResult(
        status -> {
          jobs.lockPractitioner(practitionerId);
          jobs.failStale(practitionerId, now.minus(STALE_AFTER), now);
          if (jobs.hasActive(practitionerId)) {
            throw new ProblemException(
                HttpStatus.CONFLICT,
                "EXPORT_IN_PROGRESS",
                texts.get("error.inProgress.title"),
                texts.get("error.inProgress.detail"));
          }
          jobs.insert(id, practitionerId, now);
        });
    try {
      pool.execute(() -> runner.run(id));
    } catch (RejectedExecutionException e) {
      LOG.error("account export queue full, job {} not started", id);
      jobs.markFailed(id, "export queue full", clock.instant());
    }
    return new Requested(id, statusUrl(id, practitionerId));
  }

  // ---- suivi

  /**
   * Statut du job pour son praticien (principal) ou pour le porteur de son jeton.
   *
   * @throws ProblemException 404 EXPORT_NOT_FOUND si le job n'existe pas, est à un autre praticien,
   *     ou si le jeton ne correspond pas : l'existence d'un job n'est jamais révélée.
   */
  JobStatus status(UUID jobId, Optional<UUID> principalId, String token, ExportTexts texts) {
    ExportJobView job = jobs.find(jobId).orElseThrow(() -> notFound(texts));
    boolean owner = principalId.filter(job.practitionerId()::equals).isPresent();
    if (!owner && !validToken(job, token)) {
      throw notFound(texts);
    }
    Instant now = clock.instant();
    boolean downloadable =
        "done".equals(job.status())
            && job.purgedAt() == null
            && job.expiresAt() != null
            && !now.isAfter(job.expiresAt());
    String url =
        downloadable
            ? fileUrl(job.id(), job.practitionerId(), job.expiresAt().getEpochSecond())
            : null;
    String error = "failed".equals(job.status()) ? texts.get("error.failed.detail") : null;
    return new JobStatus(
        job.id(), job.status(), job.requestedAt(), job.finishedAt(), job.expiresAt(), url, error);
  }

  // ---- téléchargement

  /**
   * Zip déchiffré d'un job terminé.
   *
   * @throws ProblemException 403 SIGNED_URL_INVALID (signature ou échéance falsifiée, job inconnu),
   *     410 EXPORT_EXPIRED (échéance passée ou fichier purgé)
   */
  ZipFile file(UUID jobId, String exp, String sig, ExportTexts texts) {
    ExportJobView job = jobs.find(jobId).orElseThrow(() -> signedUrlInvalid(texts));
    long expEpoch = parseEpoch(exp, texts);
    if (sig == null
        || !constantTimeEquals(fileSignature(job.id(), job.practitionerId(), expEpoch), sig)
        || job.expiresAt() == null
        || job.expiresAt().getEpochSecond() != expEpoch
        || !"done".equals(job.status())) {
      throw signedUrlInvalid(texts);
    }
    Instant now = clock.instant();
    if (job.purgedAt() != null
        || now.isAfter(job.expiresAt())
        || job.storageKey() == null
        || !storage.exists(job.storageKey())) {
      throw new ProblemException(
          HttpStatus.GONE,
          "EXPORT_EXPIRED",
          texts.get("error.expired.title"),
          texts.get("error.expired.detail"));
    }
    String name =
        "export-psymate-"
            + DateTimeFormatter.BASIC_ISO_DATE.format(
                job.finishedAt().atOffset(ZoneOffset.UTC).toLocalDate())
            + ".zip";
    return new ZipFile(name, storage.get(job.storageKey()));
  }

  // ---- purge

  /**
   * Supprime le fichier chiffré de chaque job terminé dont l'échéance est passée ; la ligne reste,
   * {@code purged_at} est renseigné.
   *
   * @return le nombre de fichiers purgés
   */
  int purgeDue() {
    Instant now = clock.instant();
    List<ExportJobView> due = jobs.dueForPurge(now);
    int purged = 0;
    for (ExportJobView job : due) {
      try {
        if (job.storageKey() != null) {
          storage.delete(job.storageKey());
        }
        jobs.markPurged(job.id(), now);
        purged++;
      } catch (RuntimeException e) {
        LOG.error("account export {} could not be purged", job.id(), e);
      }
    }
    return purged;
  }

  // ---- signatures

  String statusToken(UUID jobId, UUID practitionerId) {
    return storage.sign(STATUS_PREFIX + jobId + "|" + practitionerId);
  }

  private String fileSignature(UUID jobId, UUID practitionerId, long expEpoch) {
    return storage.sign(FILE_PREFIX + jobId + "|" + practitionerId + "|" + expEpoch);
  }

  private String statusUrl(UUID jobId, UUID practitionerId) {
    return "/api/me/export/" + jobId + "?token=" + statusToken(jobId, practitionerId);
  }

  private String fileUrl(UUID jobId, UUID practitionerId, long expEpoch) {
    return "/api/me/export/"
        + jobId
        + "/file?exp="
        + expEpoch
        + "&sig="
        + fileSignature(jobId, practitionerId, expEpoch);
  }

  private boolean validToken(ExportJobView job, String token) {
    return token != null && constantTimeEquals(statusToken(job.id(), job.practitionerId()), token);
  }

  private static boolean constantTimeEquals(String expected, String actual) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }

  private long parseEpoch(String exp, ExportTexts texts) {
    try {
      return Long.parseLong(exp == null ? "" : exp.trim());
    } catch (NumberFormatException e) {
      throw signedUrlInvalid(texts);
    }
  }

  // ---- erreurs

  private static ProblemException notFound(ExportTexts texts) {
    return new ProblemException(
        HttpStatus.NOT_FOUND,
        "EXPORT_NOT_FOUND",
        texts.get("error.notFound.title"),
        texts.get("error.notFound.detail"));
  }

  private static ProblemException signedUrlInvalid(ExportTexts texts) {
    return new ProblemException(
        HttpStatus.FORBIDDEN,
        "SIGNED_URL_INVALID",
        texts.get("error.url.title"),
        texts.get("error.url.detail"));
  }
}
