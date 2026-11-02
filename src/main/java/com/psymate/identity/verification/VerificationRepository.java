package com.psymate.identity.verification;

import com.psymate.identity.persistence.DocumentAccessLog;
import com.psymate.identity.persistence.DocumentAccessLogJpaRepository;
import com.psymate.identity.persistence.Practitioner;
import com.psymate.identity.persistence.PractitionerJpaRepository;
import com.psymate.identity.persistence.SecurityEvent;
import com.psymate.identity.persistence.SecurityEventJpaRepository;
import com.psymate.identity.persistence.VerificationDecisionJpaRepository;
import com.psymate.identity.persistence.VerificationDocument;
import com.psymate.identity.persistence.VerificationDocumentJpaRepository;
import com.psymate.shared.time.Timestamps;
import com.psymate.shared.web.IpAddresses;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Couture entre le service de vérification et les entités du module (practitioner,
 * verification_document, verification_decision, security_event, document_access_log). Renvoie des
 * records, jamais d'entité. Les écritures d'entité exigent une transaction (celle du service) ;
 * seule la purge planifiée tourne hors transaction et passe par un UPDATE conditionnel.
 */
@Repository
class VerificationRepository {

  /** Ligne de verification_document (clé de stockage incluse : usage interne uniquement). */
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
      String sha256,
      Instant uploadedAt,
      String reviewedStatus,
      Instant purgedAt,
      Instant deletedAt) {}

  /** Compte du praticien tel que lu en base (la session peut être en retard). */
  record AccountRow(
      UUID id,
      UUID tenantId,
      String displayName,
      String profession,
      String status,
      String locale,
      String professionalNumber,
      String phone,
      String street,
      String postalCode,
      String city,
      boolean remoteOnly) {}

  record DecisionRow(String kind, String message, Instant decidedAt) {}

  record DuePurge(UUID id, String storageKey) {}

  record AdminRecipient(String email, String locale) {}

  private final PractitionerJpaRepository practitioners;
  private final VerificationDocumentJpaRepository documents;
  private final VerificationDecisionJpaRepository decisions;
  private final SecurityEventJpaRepository events;
  private final DocumentAccessLogJpaRepository accessLogs;

  VerificationRepository(
      PractitionerJpaRepository practitioners,
      VerificationDocumentJpaRepository documents,
      VerificationDecisionJpaRepository decisions,
      SecurityEventJpaRepository events,
      DocumentAccessLogJpaRepository accessLogs) {
    this.practitioners = practitioners;
    this.documents = documents;
    this.decisions = decisions;
    this.events = events;
    this.accessLogs = accessLogs;
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
        d.getSha256(),
        Timestamps.instant(d.getUploadedAt()),
        d.getReviewedStatus(),
        Timestamps.instant(d.getPurgedAt()),
        Timestamps.instant(d.getDeletedAt()));
  }

  private static AccountRow map(Practitioner p) {
    return new AccountRow(
        p.getId(),
        p.getTenantId(),
        p.getDisplayName(),
        p.getProfession(),
        p.getVerificationStatus(),
        p.getLocale(),
        p.getProfessionalNumber(),
        p.getPhone(),
        p.getAddressStreet(),
        p.getAddressPostalCode(),
        p.getAddressCity(),
        p.isRemoteOnly());
  }

  Optional<AccountRow> findAccount(UUID id) {
    return practitioners.findById(id).map(VerificationRepository::map);
  }

  /**
   * Verrouille la ligne du praticien pour sérialiser upload, retrait et soumission. Doit être le
   * premier accès au compte dans la transaction (l'entité n'est pas relue si elle est déjà
   * chargée).
   */
  Optional<AccountRow> lockAccount(UUID id) {
    return practitioners.findForUpdate(id).map(VerificationRepository::map);
  }

  /** Justificatifs de vérification non retirés par le praticien (le recours est hors périmètre). */
  List<DocumentRow> activeDocuments(UUID practitionerId) {
    return documents.activeDocuments(practitionerId).stream()
        .map(VerificationRepository::map)
        .toList();
  }

  Optional<DocumentRow> findDocument(UUID id) {
    return documents.findById(id).map(VerificationRepository::map);
  }

  @Transactional
  void insertDocument(
      UUID id,
      UUID practitionerId,
      String type,
      String title,
      String issuer,
      Integer year,
      String storageKey,
      String mime,
      int sizeBytes,
      String sha256,
      Instant now) {
    documents.save(
        VerificationDocument.uploaded(
            id,
            practitionerId,
            type,
            title,
            issuer,
            year,
            storageKey,
            mime,
            sizeBytes,
            sha256,
            now));
  }

  /** Retrait par le praticien : la ligne reste, le fichier est supprimé (purged_at posé). */
  @Transactional
  void markDeleted(UUID id, Instant now) {
    documents.findById(id).ifPresent(d -> d.markDeleted(now));
  }

  /**
   * Appelé hors transaction par la purge planifiée : UPDATE conditionnel dans sa propre
   * transaction, sans effet si le fichier est déjà marqué purgé.
   */
  void markPurged(UUID id, Instant now) {
    documents.markPurged(id, Timestamps.utc(now));
  }

  List<DuePurge> duePurges(Instant now, int limit) {
    return documents.duePurges(Timestamps.utc(now), Limit.of(limit)).stream()
        .map(d -> new DuePurge(d.id(), d.storageKey()))
        .toList();
  }

  @Transactional
  void setStatus(UUID practitionerId, String status, Instant now) {
    practitioners.findById(practitionerId).ifPresent(p -> p.moveVerificationTo(status, now));
  }

  @Transactional
  void updateProfessionalInfo(
      UUID id,
      String professionalNumber,
      String phone,
      String street,
      String postalCode,
      String city,
      boolean remoteOnly,
      Instant now) {
    practitioners
        .findById(id)
        .ifPresent(
            p ->
                p.updateProfessionalInfo(
                    professionalNumber, phone, street, postalCode, city, remoteOnly, now));
  }

  /** Messages reçus uniquement : la note interne n'est jamais lue ici. */
  List<DecisionRow> decisionMessages(UUID practitionerId) {
    return decisions.messages(practitionerId).stream()
        .map(m -> new DecisionRow(m.kind(), m.message(), Timestamps.instant(m.decidedAt())))
        .toList();
  }

  /** Date de la dernière soumission (journal de sécurité, il n'y a pas de colonne dédiée). */
  Optional<Instant> lastSubmittedAt(UUID practitionerId) {
    return events.lastOccurredAt(practitionerId, "verification_submitted").map(Timestamps::instant);
  }

  @Transactional
  void insertSecurityEvent(UUID practitionerId, String kind, Instant now) {
    events.save(new SecurityEvent(UUID.randomUUID(), practitionerId, null, kind, null, null, now));
  }

  @Transactional
  void insertAccessLog(UUID documentId, UUID accessorId, String ip, Instant now) {
    accessLogs.save(
        new DocumentAccessLog(
            UUID.randomUUID(), documentId, accessorId, IpAddresses.parse(ip), now));
  }

  List<AdminRecipient> adminRecipients() {
    return practitioners.adminRecipients().stream()
        .map(r -> new AdminRecipient(r.email(), r.locale()))
        .toList();
  }
}
