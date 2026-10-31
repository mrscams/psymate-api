package com.psymate.identity.verification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Accès JDBC au dossier professionnel (E3.3). Lit uniquement les tables d'identity. */
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

  private static final String DOC_COLUMNS =
      "id, practitioner_id, type, title, issuer, year, storage_key, mime, size_bytes, sha256,"
          + " uploaded_at, reviewed_status, purged_at, deleted_at";

  private final JdbcClient jdbc;

  VerificationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp t = rs.getTimestamp(column);
    return t == null ? null : t.toInstant();
  }

  private static DocumentRow mapDoc(ResultSet rs, int i) throws SQLException {
    int year = rs.getInt("year");
    Integer yearValue = rs.wasNull() ? null : year;
    return new DocumentRow(
        rs.getObject("id", UUID.class),
        rs.getObject("practitioner_id", UUID.class),
        rs.getString("type"),
        rs.getString("title"),
        rs.getString("issuer"),
        yearValue,
        rs.getString("storage_key"),
        rs.getString("mime"),
        rs.getInt("size_bytes"),
        rs.getString("sha256"),
        instant(rs, "uploaded_at"),
        rs.getString("reviewed_status"),
        instant(rs, "purged_at"),
        instant(rs, "deleted_at"));
  }

  private static AccountRow mapAccount(ResultSet rs, int i) throws SQLException {
    return new AccountRow(
        rs.getObject("id", UUID.class),
        rs.getObject("tenant_id", UUID.class),
        rs.getString("display_name"),
        rs.getString("profession"),
        rs.getString("verification_status"),
        rs.getString("locale"),
        rs.getString("professional_number"),
        rs.getString("phone"),
        rs.getString("address_street"),
        rs.getString("address_postal_code"),
        rs.getString("address_city"),
        rs.getBoolean("remote_only"));
  }

  private static final String ACCOUNT_COLUMNS =
      "id, tenant_id, display_name, profession, verification_status, locale, professional_number,"
          + " phone, address_street, address_postal_code, address_city, remote_only";

  Optional<AccountRow> findAccount(UUID id) {
    return jdbc.sql("select " + ACCOUNT_COLUMNS + " from practitioner where id = :id")
        .param("id", id)
        .query(VerificationRepository::mapAccount)
        .optional();
  }

  /** Verrouille la ligne du praticien pour sérialiser upload, retrait et soumission. */
  Optional<AccountRow> lockAccount(UUID id) {
    return jdbc.sql("select " + ACCOUNT_COLUMNS + " from practitioner where id = :id for update")
        .param("id", id)
        .query(VerificationRepository::mapAccount)
        .optional();
  }

  /** Justificatifs de vérification non retirés par le praticien (le recours est hors périmètre). */
  List<DocumentRow> activeDocuments(UUID practitionerId) {
    return jdbc.sql(
            "select "
                + DOC_COLUMNS
                + " from verification_document where practitioner_id = :p"
                + " and purpose = 'verification' and deleted_at is null order by uploaded_at, id")
        .param("p", practitionerId)
        .query(VerificationRepository::mapDoc)
        .list();
  }

  Optional<DocumentRow> findDocument(UUID id) {
    return jdbc.sql("select " + DOC_COLUMNS + " from verification_document where id = :id")
        .param("id", id)
        .query(VerificationRepository::mapDoc)
        .optional();
  }

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
    jdbc.sql(
            "insert into verification_document (id, practitioner_id, purpose, type, title, issuer,"
                + " year, storage_key, mime, size_bytes, sha256, uploaded_at) values (:id, :p,"
                + " 'verification', :type, :title, :issuer, :year, :key, :mime, :size, :sha, :now)")
        .param("id", id)
        .param("p", practitionerId)
        .param("type", type)
        .param("title", title)
        .param("issuer", issuer)
        .param("year", year)
        .param("key", storageKey)
        .param("mime", mime)
        .param("size", sizeBytes)
        .param("sha", sha256)
        .param("now", Timestamp.from(now))
        .update();
  }

  /** Retrait par le praticien : la ligne reste, le fichier est supprimé (purged_at posé). */
  void markDeleted(UUID id, Instant now) {
    jdbc.sql(
            "update verification_document set deleted_at = :now, purged_at = coalesce(purged_at,"
                + " :now) where id = :id")
        .param("now", Timestamp.from(now))
        .param("id", id)
        .update();
  }

  void markPurged(UUID id, Instant now) {
    jdbc.sql(
            "update verification_document set purged_at = :now where id = :id and purged_at is null")
        .param("now", Timestamp.from(now))
        .param("id", id)
        .update();
  }

  List<DuePurge> duePurges(Instant now, int limit) {
    return jdbc.sql(
            "select id, storage_key from verification_document where purge_after < :now"
                + " and purged_at is null order by purge_after limit :limit")
        .param("now", Timestamp.from(now))
        .param("limit", limit)
        .query((rs, i) -> new DuePurge(rs.getObject("id", UUID.class), rs.getString("storage_key")))
        .list();
  }

  void setStatus(UUID practitionerId, String status, Instant now) {
    jdbc.sql("update practitioner set verification_status = :s, updated_at = :now where id = :id")
        .param("s", status)
        .param("now", Timestamp.from(now))
        .param("id", practitionerId)
        .update();
  }

  void updateProfessionalInfo(
      UUID id,
      String professionalNumber,
      String phone,
      String street,
      String postalCode,
      String city,
      boolean remoteOnly,
      Instant now) {
    jdbc.sql(
            "update practitioner set professional_number = :pro, phone = :phone,"
                + " address_street = :street, address_postal_code = :postal, address_city = :city,"
                + " remote_only = :remote, updated_at = :now where id = :id")
        .param("pro", professionalNumber)
        .param("phone", phone)
        .param("street", street)
        .param("postal", postalCode)
        .param("city", city)
        .param("remote", remoteOnly)
        .param("now", Timestamp.from(now))
        .param("id", id)
        .update();
  }

  /** Messages reçus uniquement : la note interne n'est jamais lue ici. */
  List<DecisionRow> decisionMessages(UUID practitionerId) {
    return jdbc.sql(
            "select kind, message_to_practitioner, decided_at from verification_decision"
                + " where practitioner_id = :p and message_to_practitioner is not null"
                + " order by decided_at desc, id")
        .param("p", practitionerId)
        .query(
            (rs, i) ->
                new DecisionRow(
                    rs.getString("kind"),
                    rs.getString("message_to_practitioner"),
                    instant(rs, "decided_at")))
        .list();
  }

  /** Date de la dernière soumission (journal de sécurité, il n'y a pas de colonne dédiée). */
  Optional<Instant> lastSubmittedAt(UUID practitionerId) {
    return jdbc.sql(
            "select max(occurred_at) from security_event where practitioner_id = :p"
                + " and kind = 'verification_submitted'")
        .param("p", practitionerId)
        .query((rs, i) -> instant(rs, "max"))
        .optional()
        .filter(java.util.Objects::nonNull);
  }

  void insertSecurityEvent(UUID practitionerId, String kind, Instant now) {
    jdbc.sql(
            "insert into security_event (id, practitioner_id, kind, occurred_at) values (:id, :p,"
                + " :k, :now)")
        .param("id", UUID.randomUUID())
        .param("p", practitionerId)
        .param("k", kind)
        .param("now", Timestamp.from(now))
        .update();
  }

  void insertAccessLog(UUID documentId, UUID accessorId, String ip, Instant now) {
    jdbc.sql(
            "insert into document_access_log (id, document_id, admin_id, ip, accessed_at) values"
                + " (:id, :d, :a, cast(:ip as inet), :now)")
        .param("id", UUID.randomUUID())
        .param("d", documentId)
        .param("a", accessorId)
        .param("ip", ip)
        .param("now", Timestamp.from(now))
        .update();
  }

  List<AdminRecipient> adminRecipients() {
    return jdbc.sql(
            "select email, locale from practitioner where role = 'admin'"
                + " and verification_status not in ('rejected','suspended','closed','email_pending')")
        .query((rs, i) -> new AdminRecipient(rs.getString("email"), rs.getString("locale")))
        .list();
  }
}
