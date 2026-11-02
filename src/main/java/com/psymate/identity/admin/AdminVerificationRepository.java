package com.psymate.identity.admin;

import com.psymate.identity.persistence.DocumentAccessLog;
import com.psymate.identity.persistence.DocumentAccessLogJpaRepository;
import com.psymate.identity.persistence.Practitioner;
import com.psymate.identity.persistence.PractitionerJpaRepository;
import com.psymate.identity.persistence.PractitionerJpaRepository.AdminAccount;
import com.psymate.identity.persistence.Rows;
import com.psymate.identity.persistence.SecurityEvent;
import com.psymate.identity.persistence.SecurityEventJpaRepository;
import com.psymate.identity.persistence.VerificationDecision;
import com.psymate.identity.persistence.VerificationDecisionJpaRepository;
import com.psymate.identity.persistence.VerificationDocument;
import com.psymate.identity.persistence.VerificationDocumentJpaRepository;
import com.psymate.shared.time.Timestamps;
import com.psymate.shared.web.IpAddresses;
import jakarta.persistence.Tuple;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Couture entre les services d'administration des dossiers et les entités du module. Ne touche que
 * practitioner, verification_document, verification_decision, document_access_log, security_event
 * et spring_session : aucune table patient, clinique ou de liens. Renvoie des records, jamais
 * d'entité. La file et le rappel de retard sont des lectures natives (fragment {@code
 * AdminVerificationNativeQueries}) ; les écritures d'entité s'exécutent dans la transaction du
 * service.
 */
@Repository
class AdminVerificationRepository {

  /** Ligne de la file : la clé de tri est submittedAt, ou updated_at du praticien à défaut. */
  record QueueRow(
      UUID id,
      String displayName,
      String profession,
      String city,
      int documentsCount,
      Instant submittedAt,
      Instant sortKey,
      Set<String> presentTypes) {}

  /** Compte tel que lu en base ; pas de hachage de mot de passe ni de donnée 2FA. */
  record AccountRow(
      UUID id,
      UUID tenantId,
      String email,
      String firstName,
      String lastName,
      String displayName,
      String profession,
      String locale,
      String role,
      String status,
      Instant createdAt,
      Instant emailConfirmedAt,
      Instant verifiedAt,
      String phone,
      String professionalNumber,
      String street,
      String postalCode,
      String city,
      boolean remoteOnly) {}

  /** Justificatif ; la clé de stockage reste interne et n'est jamais exposée. */
  record DocumentRow(
      UUID id,
      UUID practitionerId,
      String type,
      String title,
      String issuer,
      Integer year,
      String storageKey,
      String mime,
      int sizeBytes,
      Instant uploadedAt,
      String reviewedStatus,
      Instant reviewedAt,
      Instant purgedAt,
      Instant deletedAt) {}

  record DecisionRow(
      UUID id,
      String kind,
      String message,
      String internalNote,
      String checklistJson,
      Instant decidedAt,
      UUID adminId) {}

  record Recipient(String email, String locale) {}

  /** Dossier en attente, pour le rappel quotidien. */
  record PendingRow(String displayName, String profession, Instant submittedAt) {}

  private final PractitionerJpaRepository practitioners;
  private final VerificationDocumentJpaRepository documents;
  private final VerificationDecisionJpaRepository decisions;
  private final SecurityEventJpaRepository events;
  private final DocumentAccessLogJpaRepository accessLogs;
  private final JsonMapper json;

  AdminVerificationRepository(
      PractitionerJpaRepository practitioners,
      VerificationDocumentJpaRepository documents,
      VerificationDecisionJpaRepository decisions,
      SecurityEventJpaRepository events,
      DocumentAccessLogJpaRepository accessLogs,
      JsonMapper json) {
    this.practitioners = practitioners;
    this.documents = documents;
    this.decisions = decisions;
    this.events = events;
    this.accessLogs = accessLogs;
    this.json = json;
  }

  private static AccountRow map(AdminAccount a) {
    return new AccountRow(
        a.id(),
        a.tenantId(),
        a.email(),
        a.firstName(),
        a.lastName(),
        a.displayName(),
        a.profession(),
        a.locale(),
        a.role(),
        a.status(),
        Timestamps.instant(a.createdAt()),
        Timestamps.instant(a.emailConfirmedAt()),
        Timestamps.instant(a.verifiedAt()),
        a.phone(),
        a.professionalNumber(),
        a.street(),
        a.postalCode(),
        a.city(),
        a.remoteOnly());
  }

  private static AccountRow map(Practitioner p) {
    return new AccountRow(
        p.getId(),
        p.getTenantId(),
        p.getEmail(),
        p.getFirstName(),
        p.getLastName(),
        p.getDisplayName(),
        p.getProfession(),
        p.getLocale(),
        p.getRole(),
        p.getVerificationStatus(),
        Timestamps.instant(p.getCreatedAt()),
        Timestamps.instant(p.getEmailConfirmedAt()),
        Timestamps.instant(p.getVerifiedAt()),
        p.getPhone(),
        p.getProfessionalNumber(),
        p.getAddressStreet(),
        p.getAddressPostalCode(),
        p.getAddressCity(),
        p.isRemoteOnly());
  }

  private static DocumentRow map(VerificationDocument d) {
    return new DocumentRow(
        d.getId(),
        d.getPractitionerId(),
        d.getType(),
        d.getTitle(),
        d.getIssuer(),
        d.getYear(),
        d.getStorageKey(),
        d.getMime(),
        d.getSizeBytes(),
        Timestamps.instant(d.getUploadedAt()),
        d.getReviewedStatus(),
        Timestamps.instant(d.getReviewedAt()),
        Timestamps.instant(d.getPurgedAt()),
        Timestamps.instant(d.getDeletedAt()));
  }

  private DecisionRow map(VerificationDecision d) {
    return new DecisionRow(
        d.getId(),
        d.getKind(),
        d.getMessageToPractitioner(),
        d.getInternalNote(),
        json.writeValueAsString(d.getChecklist()),
        Timestamps.instant(d.getDecidedAt()),
        d.getAdminId());
  }

  private Map<String, Object> map(String jsonText) {
    if (jsonText == null || jsonText.isBlank()) {
      return new LinkedHashMap<>();
    }
    return json.readValue(jsonText, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  // ---- file d'attente

  /**
   * Page de la file par curseur à clé composée (clé de tri tronquée à la milliseconde, id). Une
   * seule requête native.
   *
   * @param cursorKey clé de tri du curseur en millisecondes, null pour la première page
   */
  @Transactional(readOnly = true)
  List<QueueRow> queue(
      String status, boolean oldestFirst, Long cursorKey, UUID cursorId, int limit) {
    return practitioners.adminQueue(status, oldestFirst, cursorKey, cursorId, limit).stream()
        .map(AdminVerificationRepository::queueRow)
        .toList();
  }

  private static QueueRow queueRow(Tuple t) {
    String types = Rows.string(t, "present_types");
    return new QueueRow(
        Rows.uuid(t, "id"),
        Rows.string(t, "display_name"),
        Rows.string(t, "profession"),
        Rows.string(t, "address_city"),
        Rows.integer(t, "documents_count"),
        Rows.instant(t, "submitted_at"),
        Rows.instant(t, "sort_key"),
        Arrays.stream(types == null ? new String[0] : types.split(","))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet()));
  }

  /** Dossiers under_review avec leur date de soumission, pour le rappel de retard. */
  @Transactional(readOnly = true)
  List<PendingRow> underReview() {
    return practitioners.adminUnderReview().stream()
        .map(
            t ->
                new PendingRow(
                    Rows.string(t, "display_name"),
                    Rows.string(t, "profession"),
                    Rows.instant(t, "submitted_at")))
        .toList();
  }

  // ---- dossier

  /**
   * Lecture du compte par projection : l'entité n'entre pas dans le contexte de persistance, un
   * verrou pris ensuite sur la même ligne relit donc l'état courant.
   */
  @Transactional(readOnly = true)
  Optional<AccountRow> findAccount(UUID id) {
    return practitioners.adminAccountView(id).map(AdminVerificationRepository::map);
  }

  /**
   * Verrouille la ligne du praticien pour sérialiser les décisions concurrentes. Ne pas charger
   * l'entité avant dans la même transaction (hors verrou des administrateurs actifs, qui la relit).
   */
  Optional<AccountRow> lockAccount(UUID id) {
    return practitioners.findForUpdate(id).map(AdminVerificationRepository::map);
  }

  Optional<Instant> lastSubmittedAt(UUID practitionerId) {
    return events.lastOccurredAt(practitionerId, "verification_submitted").map(Timestamps::instant);
  }

  /** Justificatifs de vérification non retirés par le praticien. */
  List<DocumentRow> activeDocuments(UUID practitionerId) {
    return documents.activeDocuments(practitionerId).stream()
        .map(AdminVerificationRepository::map)
        .toList();
  }

  /** Justificatif d'un praticien donné, retiré ou non (l'appelant filtre deleted_at). */
  Optional<DocumentRow> findDocument(UUID practitionerId, UUID documentId) {
    return documents
        .findByIdAndPractitionerId(documentId, practitionerId)
        .map(AdminVerificationRepository::map);
  }

  @Transactional
  void reviewDocument(UUID documentId, String status, UUID adminId, Instant now) {
    documents.findById(documentId).ifPresent(d -> d.review(status, adminId, now));
  }

  /** Décisions du plus récent au plus ancien. */
  List<DecisionRow> decisions(UUID practitionerId) {
    return decisions.findByPractitionerIdOrderByDecidedAtDescIdAsc(practitionerId).stream()
        .map(this::map)
        .toList();
  }

  // ---- écritures de la décision

  void insertDecision(
      UUID id,
      UUID practitionerId,
      UUID adminId,
      String kind,
      String internalNote,
      String message,
      String checklistJson,
      Instant now) {
    insertDecision(
        id, practitionerId, adminId, kind, internalNote, message, checklistJson, null, now);
  }

  /** related_id : recours concerné pour appeal_accepted et appeal_rejected (M1-14b). */
  @Transactional
  void insertDecision(
      UUID id,
      UUID practitionerId,
      UUID adminId,
      String kind,
      String internalNote,
      String message,
      String checklistJson,
      UUID relatedId,
      Instant now) {
    decisions.save(
        new VerificationDecision(
            id,
            practitionerId,
            adminId,
            kind,
            internalNote,
            message,
            map(checklistJson),
            relatedId,
            now));
  }

  /** Le compte est déjà verrouillé par {@link #lockAccount} dans la même transaction. */
  @Transactional
  void markApproved(UUID practitionerId, UUID adminId, Instant now) {
    practitioners.findById(practitionerId).ifPresent(p -> p.markApproved(adminId, now));
  }

  @Transactional
  void markNeedsMore(UUID practitionerId, Instant now) {
    practitioners.findById(practitionerId).ifPresent(p -> p.markNeedsMore(now));
  }

  @Transactional
  void markRejected(UUID practitionerId, Instant now, Instant purgeAfter) {
    practitioners.findById(practitionerId).ifPresent(p -> p.markRejected(now, purgeAfter));
  }

  /** Révoque toutes les sessions du praticien ; renvoie leur nombre. */
  int deleteSessions(UUID practitionerId) {
    return practitioners.deleteSessions(practitionerId);
  }

  /** Planifie la purge des fichiers du dossier (décision + 90 jours). */
  void schedulePurge(UUID practitionerId, Instant purgeAfter) {
    documents.schedulePurge(practitionerId, Timestamps.utc(purgeAfter));
  }

  // ---- journal

  @Transactional
  void insertAccessLog(UUID documentId, UUID adminId, String ip, Instant now) {
    accessLogs.save(
        new DocumentAccessLog(UUID.randomUUID(), documentId, adminId, IpAddresses.parse(ip), now));
  }

  /** Journal d'administration (G Q29) : security_event avec l'administrateur comme acteur. */
  @Transactional
  void insertAdminEvent(
      UUID practitionerId, UUID adminId, String kind, String ip, String detailsJson, Instant now) {
    events.save(
        new SecurityEvent(
            UUID.randomUUID(),
            practitionerId,
            adminId,
            kind,
            IpAddresses.parse(ip),
            detailsJson == null ? null : map(detailsJson),
            now));
  }

  @Transactional(readOnly = true)
  List<Recipient> adminRecipients() {
    return practitioners.adminRecipientsByEmail().stream()
        .map(r -> new Recipient(r.email(), r.locale()))
        .toList();
  }
}
