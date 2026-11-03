package com.psymate.clinical.export.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.clinical.export.ExportTestSupport;
import com.psymate.clinical.export.model.ExportJob;
import com.psymate.clinical.export.repository.ExportJobRepository.ExportJobView;
import com.psymate.clinical.export.service.ExportJobRunner;
import com.psymate.clinical.export.service.ExportJobService;
import com.psymate.clinical.export.service.ExportJobService.Requested;
import com.psymate.clinical.export.service.ExportTexts;
import com.psymate.shared.problem.ProblemException;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persistance JPA de {@code export_job} (M8-06, G Q32) : aller-retour de l'entité, cycle de statut
 * (queued → running → done / failed) sous verrou de ligne, requalification des jobs interrompus,
 * échéances de purge, idempotence de {@code markPurged}, et la demande concurrente : un seul job
 * par praticien, l'autre demande en 409, l'INSERT partant au commit sous le verrou advisory. Vraie
 * base, {@code ddl-auto=validate} : le contexte ne démarre que si l'entité colle au schéma V11.
 */
class ExportPersistenceIT extends ExportTestSupport {

  @Autowired private ExportJobRepository jobs;
  @Autowired private ExportJobService service;
  @Autowired private TransactionTemplate tx;
  @Autowired private EntityManagerFactory emf;

  @Value("${spring.jpa.hibernate.ddl-auto}")
  private String ddlAuto;

  private UUID doctor;
  private Instant now;

  @BeforeEach
  void setUp() {
    doctor = account("Dr Persistance", "approved");
    now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    clock.set(now);
  }

  // ---- helpers

  /** Nouvelle demande dans la transaction qu'exige l'adaptateur, comme le fait le service. */
  private UUID queued(UUID practitioner, Instant at) {
    UUID id = UUID.randomUUID();
    tx.executeWithoutResult(
        s -> {
          jobs.lockPractitioner(practitioner);
          jobs.insert(id, practitioner, at);
        });
    return id;
  }

  /** Ligne insérée directement, pour les cas que l'application ne produit pas elle-même. */
  private UUID row(UUID practitioner, String status, Instant requestedAt, Instant expiresAt) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into export_job (id, practitioner_id, status, requested_at, storage_key,"
            + " expires_at) values (?, ?, ?, ?, ?, ?)",
        id,
        practitioner,
        status,
        Timestamp.from(requestedAt),
        "exports/" + id + ".zip",
        expiresAt == null ? null : Timestamp.from(expiresAt));
    return id;
  }

  private String status(UUID id) {
    return jdbc.queryForObject("select status from export_job where id = ?", String.class, id);
  }

  private ExportJobView view(UUID id) {
    return jobs.find(id).orElseThrow();
  }

  /** {@code hasActive} dans la transaction qu'il exige. */
  private boolean active(UUID practitioner) {
    return Boolean.TRUE.equals(tx.execute(s -> jobs.hasActive(practitioner)));
  }

  private static <T> List<T> concurrently(List<Callable<T>> tasks) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    try {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<T>> futures = new ArrayList<>();
      for (Callable<T> task : tasks) {
        futures.add(
            pool.submit(
                () -> {
                  go.await(10, TimeUnit.SECONDS);
                  return task.call();
                }));
      }
      go.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> f : futures) {
        results.add(f.get(60, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  // ---- mapping

  @Test
  void theEntityMapsTheNineColumnsOfV11AndValidateIsOn() {
    assertThat(ddlAuto).isEqualTo("validate");
    assertThat(emf.getMetamodel().entity(ExportJob.class).getAttributes())
        .extracting(a -> a.getName())
        .containsExactlyInAnyOrder(
            "id",
            "practitionerId",
            "status",
            "requestedAt",
            "finishedAt",
            "storageKey",
            "expiresAt",
            "purgedAt",
            "error");
  }

  @Test
  void aNewJobRoundTripsAsAQueuedView() {
    UUID id = queued(doctor, now);

    ExportJobView v = view(id);
    assertThat(v.id()).isEqualTo(id);
    assertThat(v.practitionerId()).isEqualTo(doctor);
    assertThat(v.status()).isEqualTo("queued");
    assertThat(v.requestedAt()).isEqualTo(now);
    assertThat(v.finishedAt()).isNull();
    assertThat(v.storageKey()).isNull();
    assertThat(v.expiresAt()).isNull();
    assertThat(v.purgedAt()).isNull();
    assertThat(v.error()).isNull();
    assertThat(jobs.find(UUID.randomUUID())).isEmpty();
  }

  @Test
  void theRequestStepsRefuseToRunOutsideTheServiceTransaction() {
    assertThatThrownBy(() -> jobs.hasActive(doctor))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> jobs.insert(UUID.randomUUID(), doctor, now))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> jobs.lockPractitioner(doctor))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> jobs.failStale(doctor, now, now))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  // ---- cycle de statut

  @Test
  void aJobGoesQueuedRunningDoneAndCannotBeTakenTwice() {
    UUID id = queued(doctor, now);

    assertThat(jobs.markRunning(id)).isTrue();
    assertThat(status(id)).isEqualTo("running");
    assertThat(jobs.markRunning(id)).as("déjà pris").isFalse();

    Instant finished = now.plusSeconds(30);
    Instant expires = finished.plus(ExportJobService.RETENTION);
    jobs.markDone(id, "exports/" + id + ".zip", finished, expires);

    ExportJobView v = view(id);
    assertThat(v.status()).isEqualTo("done");
    assertThat(v.storageKey()).isEqualTo("exports/" + id + ".zip");
    assertThat(v.finishedAt()).isEqualTo(finished);
    assertThat(v.expiresAt()).isEqualTo(expires);
    assertThat(v.error()).isNull();
    assertThat(v.purgedAt()).isNull();
    assertThat(jobs.markRunning(id)).as("terminé").isFalse();
    assertThat(jobs.markRunning(UUID.randomUUID())).as("inconnu").isFalse();
  }

  @Test
  void aFailedJobKeepsItsErrorAndIsNotTakenAgain() {
    UUID id = queued(doctor, now);
    assertThat(jobs.markRunning(id)).isTrue();
    jobs.markFailed(id, "x".repeat(ExportJobRunner.MAX_ERROR), now.plusSeconds(5));

    ExportJobView v = view(id);
    assertThat(v.status()).isEqualTo("failed");
    assertThat(v.error()).hasSize(ExportJobRunner.MAX_ERROR);
    assertThat(v.finishedAt()).isEqualTo(now.plusSeconds(5));
    assertThat(v.storageKey()).isNull();
    assertThat(jobs.markRunning(id)).isFalse();

    // Une fin heureuse efface l'erreur d'une tentative précédente (sémantique de l'ancien UPDATE).
    jobs.markDone(id, "exports/" + id + ".zip", now.plusSeconds(9), now.plusSeconds(99));
    assertThat(view(id).error()).isNull();
    assertThat(view(id).status()).isEqualTo("done");
  }

  @Test
  void onlyOneOfFourConcurrentWorkersTakesTheJob() throws Exception {
    UUID id = queued(doctor, now);
    List<Callable<Boolean>> workers = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      workers.add(() -> jobs.markRunning(id));
    }
    List<Boolean> taken = concurrently(workers);
    assertThat(taken).containsExactlyInAnyOrder(true, false, false, false);
    assertThat(status(id)).isEqualTo("running");
  }

  // ---- demande : jobs interrompus et job actif

  @Test
  void failStaleOnlyRequalifiesOldActiveJobsOfThatPractitioner() {
    Instant old = now.minus(Duration.ofHours(3));
    UUID staleRunning = row(doctor, "running", old, null);
    UUID staleQueued = row(doctor, "queued", old, null);
    UUID oldDone = row(doctor, "done", old, now.plusSeconds(60));
    UUID oldFailed = row(doctor, "failed", old, null);
    UUID recentQueued = row(doctor, "queued", now.minusSeconds(60), null);
    UUID other = account("Dr Autre", "approved");
    UUID otherStale = row(other, "running", old, null);

    tx.executeWithoutResult(
        s -> {
          jobs.lockPractitioner(doctor);
          assertThat(jobs.hasActive(doctor)).isTrue();
          jobs.failStale(doctor, now.minus(ExportJobService.STALE_AFTER), now);
          assertThat(jobs.hasActive(doctor)).as("le job récent bloque encore").isTrue();
        });

    for (UUID id : List.of(staleRunning, staleQueued)) {
      ExportJobView v = view(id);
      assertThat(v.status()).isEqualTo("failed");
      assertThat(v.error()).isEqualTo("interrupted");
      assertThat(v.finishedAt()).isEqualTo(now);
    }
    assertThat(status(oldDone)).isEqualTo("done");
    assertThat(status(oldFailed)).isEqualTo("failed");
    assertThat(view(oldFailed).error()).isNull();
    assertThat(status(recentQueued)).isEqualTo("queued");
    assertThat(status(otherStale)).isEqualTo("running");
  }

  @Test
  void hasActiveSeesQueuedAndRunningOnly() {
    UUID other = account("Dr Autre", "approved");
    row(other, "running", now, null);
    row(doctor, "done", now, now.plusSeconds(60));
    row(doctor, "failed", now, null);
    assertThat(active(doctor)).isFalse();

    UUID q = row(doctor, "queued", now, null);
    assertThat(active(doctor)).isTrue();
    jdbc.update("update export_job set status = 'running' where id = ?", q);
    assertThat(active(doctor)).isTrue();
  }

  /**
   * Test sensible au timing, vu une fois en CI (PR #81, tranche clinical, graine 79492725344 : deux
   * jobs au lieu d'un job et d'un 409). {@code request()} lance l'export en asynchrone dès le
   * commit ({@code pool.execute}) ; ce compte n'a aucun patient, donc le premier job peut finir
   * avant que la seconde demande prenne le verrou et lise {@code hasActive}, et elle crée alors
   * légitimement un second job. Laissé tel quel à la demande de l'utilisateur (3 novembre 2026) :
   * si l'échec se répète, empêcher le job de finir pendant le test (exécuteur bloqué, ou statut
   * relu juste après l'insertion) plutôt que d'allonger une attente.
   */
  @Test
  void twoConcurrentRequestsForOnePractitionerGiveOneJobAndOne409() throws Exception {
    ExportTexts texts = ExportTexts.of(Locale.FRENCH);
    Callable<Object> request =
        () -> {
          try {
            return service.request(doctor, texts);
          } catch (ProblemException e) {
            return e;
          }
        };
    List<Object> outcomes = concurrently(List.of(request, request));

    assertThat(outcomes).hasSize(2);
    assertThat(outcomes.stream().filter(Requested.class::isInstance)).hasSize(1);
    ProblemException refused =
        outcomes.stream()
            .filter(ProblemException.class::isInstance)
            .map(ProblemException.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(refused.status().value()).isEqualTo(409);
    assertThat(refused.code()).isEqualTo("EXPORT_IN_PROGRESS");
    assertThat(count("select count(*) from export_job where practitioner_id = ?", doctor))
        .isEqualTo(1);
  }

  // ---- purge

  @Test
  void dueForPurgeOrdersByExpiryAndSkipsPendingPurgedAndFutureJobs() {
    UUID late = row(doctor, "done", now.minusSeconds(900), now.minusSeconds(100));
    UUID early = row(doctor, "done", now.minusSeconds(900), now.minusSeconds(300));
    UUID exactlyNow = row(doctor, "done", now.minusSeconds(900), now);
    UUID future = row(doctor, "done", now.minusSeconds(900), now.plusSeconds(100));
    UUID purged = row(doctor, "done", now.minusSeconds(900), now.minusSeconds(200));
    jdbc.update("update export_job set purged_at = ? where id = ?", Timestamp.from(now), purged);
    UUID stillRunning = row(doctor, "running", now.minusSeconds(900), now.minusSeconds(500));
    UUID failed = row(doctor, "failed", now.minusSeconds(900), now.minusSeconds(500));

    List<UUID> due = jobs.dueForPurge(now).stream().map(ExportJobView::id).toList();

    assertThat(due).containsSubsequence(early, late);
    assertThat(due).doesNotContain(exactlyNow, future, purged, stillRunning, failed);
    assertThat(jobs.dueForPurge(now.plusSeconds(1)).stream().map(ExportJobView::id))
        .contains(exactlyNow);
  }

  @Test
  void markPurgedIsIdempotentAndKeepsTheFirstDate() {
    UUID id = row(doctor, "done", now.minusSeconds(900), now.minusSeconds(100));
    assertThat(jobs.dueForPurge(now).stream().map(ExportJobView::id)).contains(id);

    jobs.markPurged(id, now);
    jobs.markPurged(id, now.plusSeconds(3600));

    ExportJobView v = view(id);
    assertThat(v.purgedAt()).isEqualTo(now);
    assertThat(v.status()).as("la ligne reste").isEqualTo("done");
    assertThat(v.storageKey()).isNotNull();
    assertThat(jobs.dueForPurge(now.plusSeconds(7200)).stream().map(ExportJobView::id))
        .doesNotContain(id);
    // Un job inconnu : rien, sans erreur.
    jobs.markPurged(UUID.randomUUID(), now);
  }
}
