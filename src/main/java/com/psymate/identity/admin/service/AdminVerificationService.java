package com.psymate.identity.admin.service;

import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.admin.dto.AddressDto;
import com.psymate.identity.admin.dto.ContactDto;
import com.psymate.identity.admin.dto.DecisionDto;
import com.psymate.identity.admin.dto.DecisionItemDto;
import com.psymate.identity.admin.dto.DecisionRequest;
import com.psymate.identity.admin.dto.DocumentDto;
import com.psymate.identity.admin.dto.DossierDto;
import com.psymate.identity.admin.dto.QueueDto;
import com.psymate.identity.admin.dto.QueueItemDto;
import com.psymate.identity.admin.dto.SignedUrlDto;
import com.psymate.identity.admin.model.AdminException;
import com.psymate.identity.admin.model.AdminException.FieldError;
import com.psymate.identity.admin.model.BusinessDays;
import com.psymate.identity.admin.repository.AdminVerificationQueries;
import com.psymate.identity.admin.repository.AdminVerificationQueries.AccountRow;
import com.psymate.identity.admin.repository.AdminVerificationQueries.DocumentRow;
import com.psymate.identity.admin.repository.AdminVerificationQueries.PendingRow;
import com.psymate.identity.admin.repository.AdminVerificationQueries.QueueRow;
import com.psymate.identity.admin.repository.AdminVerificationQueries.Recipient;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerApproved;
import com.psymate.identity.api.PractitionerRejected;
import com.psymate.identity.verification.service.DocumentCipher;
import com.psymate.identity.verification.service.StorageService;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validation des dossiers professionnels par l'administration (A10.1, UC-17, fiche 20).
 *
 * <p>Toute décision est un INSERT dans verification_decision (append-only) et une ligne du journal
 * d'administration, la table security_event (G Q29). Aucun accès aux données patient.
 */
@Service
@Transactional
public class AdminVerificationService {

  private static final Logger LOG = LoggerFactory.getLogger(AdminVerificationService.class);

  static final Duration URL_VALIDITY = Duration.ofMinutes(5);
  static final int DEFAULT_LIMIT = 50;
  static final int MAX_LIMIT = 200;
  static final int MAX_TEXT = 4000;
  static final int MAX_CHECKLIST_CHARS = 20_000;

  /** Délai de traitement attendu d'un dossier soumis, en jours ouvrés (fiche 20). */
  static final int SLA_BUSINESS_DAYS = 2;

  /** Fichiers du dossier conservés 90 jours après la décision (R12). */
  static final Duration DOCUMENT_RETENTION = Duration.ofDays(90);

  /** Compte refusé : purge programmée 30 jours après le refus (A10, fiche 27). */
  static final Duration REJECTED_RETENTION = Duration.ofDays(30);

  static final Set<String> QUEUE_STATUSES = Set.of("under_review", "needs_more");
  static final Set<String> DECISION_KINDS = Set.of("approved", "needs_more", "rejected");
  static final Set<String> REVIEW_STATUSES = Set.of("pending", "conform", "non_conform");

  /** Pièces obligatoires pour toutes les professions (G Q14), comme CompletenessRules. */
  private static final List<String> REQUIRED_TYPES = List.of("diploma", "identity");

  private static final Pattern IP = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

  /** Fichier déchiffré prêt à être servi. */
  public record FileContent(byte[] bytes, String mime) {}

  private final AdminVerificationQueries repo;
  private final StorageService storage;
  private final DocumentCipher cipher;
  private final AdminMailer mailer;
  private final AdminMessages messages;
  private final DomainEventPublisher events;
  private final JsonMapper json;
  private final Clock clock;
  private final ExecutorService mailExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "admin-mail");
            t.setDaemon(true);
            return t;
          });

  AdminVerificationService(
      AdminVerificationQueries repo,
      StorageService storage,
      DocumentCipher cipher,
      AdminMailer mailer,
      AdminMessages messages,
      DomainEventPublisher events,
      JsonMapper json,
      Clock clock) {
    this.repo = repo;
    this.storage = storage;
    this.cipher = cipher;
    this.mailer = mailer;
    this.messages = messages;
    this.events = events;
    this.json = json;
    this.clock = clock;
  }

  @PreDestroy
  void shutdown() {
    mailExecutor.shutdown();
  }

  // ---- file d'attente

  @Transactional(readOnly = true)
  public QueueDto list(String status, String sort, String cursor, Integer limit) {
    String st = status == null ? "under_review" : status;
    if (!QUEUE_STATUSES.contains(st)) {
      throw AdminException.fields(new FieldError("status", "INVALID_STATUS", "admin.error.status"));
    }
    String so = sort == null ? "oldest" : sort;
    if (!so.equals("oldest") && !so.equals("newest")) {
      throw AdminException.fields(new FieldError("sort", "INVALID_SORT", "admin.error.sort"));
    }
    int max = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
    Long cursorKey = null;
    UUID cursorId = null;
    if (cursor != null && !cursor.isBlank()) {
      try {
        String[] parts =
            new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
        if (parts.length != 2) {
          throw new IllegalArgumentException("cursor");
        }
        cursorKey = Long.valueOf(parts[0]);
        cursorId = UUID.fromString(parts[1]);
      } catch (IllegalArgumentException e) {
        throw AdminException.fields(
            new FieldError("cursor", "INVALID_CURSOR", "admin.error.cursor"));
      }
    }
    List<QueueRow> rows = repo.queue(st, so.equals("oldest"), cursorKey, cursorId, max + 1);
    boolean more = rows.size() > max;
    List<QueueRow> page = more ? rows.subList(0, max) : rows;
    Instant now = clock.instant();
    boolean underReview = st.equals("under_review");
    List<QueueItemDto> items =
        page.stream()
            .map(
                r ->
                    new QueueItemDto(
                        r.id(),
                        r.displayName(),
                        r.profession(),
                        r.city(),
                        r.documentsCount(),
                        r.submittedAt(),
                        underReview && overdue(r.submittedAt(), now),
                        REQUIRED_TYPES.stream()
                            .filter(t -> !r.presentTypes().contains(t))
                            .toList()))
            .toList();
    String next = null;
    if (more) {
      QueueRow last = page.get(page.size() - 1);
      next =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  (last.sortKey().toEpochMilli() + "|" + last.id())
                      .getBytes(StandardCharsets.UTF_8));
    }
    return new QueueDto(items, next);
  }

  // ---- dossier

  @Transactional(readOnly = true)
  public DossierDto get(UUID practitionerId) {
    AccountRow a = dossier(practitionerId);
    Instant submittedAt = repo.lastSubmittedAt(practitionerId).orElse(null);
    List<DocumentDto> docs =
        repo.activeDocuments(practitionerId).stream().map(this::toDto).toList();
    List<DecisionItemDto> decisions =
        repo.decisions(practitionerId).stream()
            .map(
                d ->
                    new DecisionItemDto(
                        d.id(),
                        d.kind(),
                        d.message(),
                        d.internalNote(),
                        checklist(d.checklistJson()),
                        d.decidedAt(),
                        d.adminId()))
            .toList();
    return new DossierDto(
        a.id(),
        a.displayName(),
        a.firstName(),
        a.lastName(),
        a.email(),
        a.profession(),
        a.locale(),
        a.status(),
        a.createdAt(),
        a.emailConfirmedAt(),
        submittedAt,
        "under_review".equals(a.status()) && overdue(submittedAt, clock.instant()),
        a.verifiedAt(),
        new ContactDto(
            a.phone(),
            a.professionalNumber(),
            new AddressDto(a.street(), a.postalCode(), a.city()),
            a.remoteOnly()),
        docs,
        decisions);
  }

  // ---- justificatifs

  @Transactional(readOnly = true)
  public SignedUrlDto signedUrl(UUID adminId, UUID practitionerId, UUID documentId) {
    DocumentRow doc = document(practitionerId, documentId);
    if (doc.purgedAt() != null) {
      throw purged();
    }
    long exp = clock.instant().plus(URL_VALIDITY).getEpochSecond();
    String sig = cipher.sign(payload(documentId, adminId, exp));
    return new SignedUrlDto(
        "/api/admin/verifications/"
            + practitionerId
            + "/documents/"
            + documentId
            + "/file?exp="
            + exp
            + "&sig="
            + sig,
        Instant.ofEpochSecond(exp));
  }

  /**
   * Sert le fichier déchiffré après contrôle de la signature et de l'échéance. Chaque lecture écrit
   * une ligne document_access_log et une ligne du journal d'administration (admin.document_read).
   */
  public FileContent file(
      UUID adminId, UUID practitionerId, UUID documentId, Long exp, String sig, String ip) {
    DocumentRow doc = document(practitionerId, documentId);
    Instant now = clock.instant();
    boolean valid =
        exp != null
            && sig != null
            && exp >= now.getEpochSecond()
            && MessageDigest.isEqual(
                cipher.sign(payload(documentId, adminId, exp)).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    if (!valid) {
      throw AdminException.of(HttpStatus.FORBIDDEN, "ADMIN_SIGNED_URL_INVALID", "admin.error.url");
    }
    if (doc.purgedAt() != null) {
      throw purged();
    }
    byte[] plain = cipher.decrypt(storage.get(doc.storageKey()), doc.storageKey());
    String cleanIp = normalizeIp(ip);
    repo.insertAccessLog(doc.id(), adminId, cleanIp, now);
    repo.insertAdminEvent(
        practitionerId,
        adminId,
        "admin.document_read",
        cleanIp,
        write(Map.of("documentId", doc.id())),
        now);
    return new FileContent(plain, doc.mime());
  }

  public DocumentDto review(
      UUID adminId, UUID practitionerId, UUID documentId, String reviewedStatus) {
    if (reviewedStatus == null || !REVIEW_STATUSES.contains(reviewedStatus)) {
      throw AdminException.fields(
          new FieldError("reviewedStatus", "INVALID_REVIEWED_STATUS", "admin.error.reviewed"));
    }
    DocumentRow doc = document(practitionerId, documentId);
    repo.reviewDocument(doc.id(), reviewedStatus, adminId, clock.instant());
    return toDto(document(practitionerId, documentId));
  }

  // ---- décision

  public DecisionDto decide(
      CurrentPractitioner admin, UUID practitionerId, DecisionRequest r, String ip) {
    String kind = r.kind();
    if (kind == null || !DECISION_KINDS.contains(kind)) {
      throw AdminException.fields(new FieldError("kind", "INVALID_KIND", "admin.error.kind"));
    }
    String message = blankToNull(r.messageToPractitioner());
    String note = blankToNull(r.internalNote());
    boolean force = Boolean.TRUE.equals(r.force());
    if ((kind.equals("needs_more") || kind.equals("rejected")) && message == null) {
      throw AdminException.fields(
          new FieldError("messageToPractitioner", "MESSAGE_REQUIRED", "admin.error.message"));
    }
    if (message != null && message.length() > MAX_TEXT) {
      throw AdminException.fields(
          new FieldError("messageToPractitioner", "TOO_LONG", "admin.error.toolong"));
    }
    if (note != null && note.length() > MAX_TEXT) {
      throw AdminException.fields(
          new FieldError("internalNote", "TOO_LONG", "admin.error.toolong"));
    }
    if (kind.equals("approved") && force && note == null) {
      throw AdminException.fields(
          new FieldError("internalNote", "NOTE_REQUIRED", "admin.error.note"));
    }
    Map<String, Object> checklist = r.checklist() == null ? Map.of() : r.checklist();
    String checklistJson = write(checklist);
    if (checklistJson.length() > MAX_CHECKLIST_CHARS) {
      throw AdminException.fields(
          new FieldError("checklist", "TOO_LARGE", "admin.error.checklist"));
    }

    AccountRow target =
        repo.lockAccount(practitionerId)
            .filter(a -> !"admin".equals(a.role()))
            .orElseThrow(AdminVerificationService::notFound);
    if (!target.status().equals("under_review") && !target.status().equals("needs_more")) {
      throw AdminException.of(
          HttpStatus.CONFLICT, "ADMIN_DOSSIER_NOT_REVIEWABLE", "admin.error.notreviewable");
    }
    if (kind.equals("approved") && !force) {
      List<DocumentRow> live =
          repo.activeDocuments(practitionerId).stream().filter(d -> d.purgedAt() == null).toList();
      List<UUID> pending =
          live.stream()
              .filter(d -> !"conform".equals(d.reviewedStatus()))
              .map(DocumentRow::id)
              .toList();
      if (live.isEmpty() || !pending.isEmpty()) {
        throw AdminException.with(
            HttpStatus.CONFLICT,
            "ADMIN_APPROVAL_INCOMPLETE",
            "admin.error.incomplete",
            Map.of("pendingDocuments", pending));
      }
    }

    Instant now = clock.instant();
    UUID decisionId = UUID.randomUUID();
    String cleanIp = normalizeIp(ip);
    repo.insertDecision(
        decisionId, practitionerId, admin.id(), kind, note, message, checklistJson, now);
    String newStatus;
    switch (kind) {
      case "approved" -> {
        repo.markApproved(practitionerId, admin.id(), now);
        newStatus = "approved";
        events.publish(
            new PractitionerApproved(target.tenantId(), practitionerId, target.profession()));
        mailAfterCommit(target, l -> mailer.sendApproved(target.email(), l, target.displayName()));
      }
      case "needs_more" -> {
        repo.markNeedsMore(practitionerId, now);
        newStatus = "needs_more";
        mailAfterCommit(
            target, l -> mailer.sendNeedsMore(target.email(), l, target.displayName(), message));
      }
      default -> {
        repo.markRejected(practitionerId, now, now.plus(REJECTED_RETENTION));
        newStatus = "rejected";
        int revoked = repo.deleteSessions(practitionerId);
        repo.insertAdminEvent(
            practitionerId,
            admin.id(),
            "session_revoked",
            cleanIp,
            write(Map.of("reason", "rejected", "count", revoked)),
            now);
        events.publish(
            new PractitionerRejected(target.tenantId(), practitionerId, target.profession()));
        mailAfterCommit(
            target, l -> mailer.sendRejected(target.email(), l, target.displayName(), message));
      }
    }
    repo.schedulePurge(practitionerId, now.plus(DOCUMENT_RETENTION));
    repo.insertAdminEvent(
        practitionerId,
        admin.id(),
        "admin.decision",
        cleanIp,
        write(Map.of("kind", kind, "decisionId", decisionId)),
        now);
    return new DecisionDto(decisionId, kind, message, note, checklist, now, admin.id(), newStatus);
  }

  // ---- rappel quotidien

  /**
   * Envoie à chaque administrateur actif un e-mail listant les dossiers under_review dont le délai
   * est dépassé. Renvoie le nombre de dossiers en retard (aucun e-mail s'il n'y en a pas).
   */
  @Transactional(readOnly = true)
  public int sendOverdueReminders() {
    Instant now = clock.instant();
    List<PendingRow> overdue =
        repo.underReview().stream().filter(p -> overdue(p.submittedAt(), now)).toList();
    if (overdue.isEmpty()) {
      return 0;
    }
    for (Recipient admin : repo.adminRecipients()) {
      Locale locale = locale(admin.locale());
      DateTimeFormatter date =
          DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
              .withLocale(AdminMessages.normalize(locale))
              .withZone(ZoneOffset.UTC);
      StringBuilder list = new StringBuilder();
      for (PendingRow p : overdue) {
        list.append("- ")
            .append(p.displayName())
            .append(" — ")
            .append(messages.get("admin.profession." + p.profession(), locale))
            .append(" — ")
            .append(
                messages.get("admin.mail.overdue.submitted", locale, date.format(p.submittedAt())))
            .append('\n');
      }
      try {
        mailer.sendOverdue(admin.email(), locale, overdue.size(), list.toString());
      } catch (RuntimeException e) {
        LOG.error("overdue reminder could not be sent", e);
      }
    }
    return overdue.size();
  }

  // ---- utilitaires

  /** Vrai si la soumission date de plus de 2 jours ouvrés ; faux sans date de soumission. */
  static boolean overdue(Instant submittedAt, Instant now) {
    return submittedAt != null
        && BusinessDays.addBusinessDays(submittedAt, SLA_BUSINESS_DAYS).isBefore(now);
  }

  private AccountRow dossier(UUID id) {
    return repo.findAccount(id)
        .filter(a -> !"admin".equals(a.role()))
        .orElseThrow(AdminVerificationService::notFound);
  }

  /** Justificatif du praticien : 404 s'il n'existe pas, a été retiré ou est à un autre. */
  private DocumentRow document(UUID practitionerId, UUID documentId) {
    return repo.findDocument(practitionerId, documentId)
        .filter(d -> d.deletedAt() == null)
        .orElseThrow(
            () ->
                AdminException.of(
                    HttpStatus.NOT_FOUND, "ADMIN_DOCUMENT_NOT_FOUND", "admin.error.document"));
  }

  private static AdminException notFound() {
    return AdminException.of(
        HttpStatus.NOT_FOUND, "ADMIN_DOSSIER_NOT_FOUND", "admin.error.dossier");
  }

  private static AdminException purged() {
    return AdminException.of(HttpStatus.GONE, "ADMIN_DOCUMENT_PURGED", "admin.error.purged");
  }

  private DocumentDto toDto(DocumentRow d) {
    return new DocumentDto(
        d.id(),
        d.type(),
        d.title(),
        d.issuer(),
        d.year(),
        d.mime(),
        d.sizeBytes(),
        d.uploadedAt(),
        d.reviewedStatus(),
        d.reviewedAt(),
        d.purgedAt() != null);
  }

  private static String payload(UUID documentId, UUID adminId, long exp) {
    return "admin|" + documentId + "|" + adminId + "|" + exp;
  }

  private Map<String, Object> checklist(String jsonText) {
    if (jsonText == null) {
      return Map.of();
    }
    return json.readValue(jsonText, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  private String write(Object value) {
    return json.writeValueAsString(value);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static Locale locale(String tag) {
    return Locale.forLanguageTag(tag == null ? "fr" : tag);
  }

  private static String normalizeIp(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.trim();
    int zone = s.indexOf('%');
    if (zone >= 0) {
      s = s.substring(0, zone);
    }
    return IP.matcher(s).matches() && (s.contains(".") || s.contains(":")) ? s : null;
  }

  /** E-mail au praticien dans sa langue, après le commit, sur un exécuteur mono-thread. */
  private void mailAfterCommit(AccountRow target, java.util.function.Consumer<Locale> send) {
    Locale locale = locale(target.locale());
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            mailExecutor.execute(
                () -> {
                  try {
                    send.accept(locale);
                  } catch (RuntimeException e) {
                    LOG.error("decision e-mail could not be sent", e);
                  }
                });
          }
        });
  }
}
