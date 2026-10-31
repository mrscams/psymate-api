package com.psymate.identity.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accès JDBC de l'administration des dossiers. Ne lit que practitioner, verification_document,
 * verification_decision, document_access_log, security_event et spring_session : aucune table
 * patient, clinique ou de liens.
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

  private static final String ACCOUNT_COLUMNS =
      "id, tenant_id, email, first_name, last_name, display_name, profession, locale, role,"
          + " verification_status, created_at, email_confirmed_at, verified_at, phone,"
          + " professional_number, address_street, address_postal_code, address_city, remote_only";

  private static final String DOC_COLUMNS =
      "id, practitioner_id, type, title, issuer, year, storage_key, mime, size_bytes, uploaded_at,"
          + " reviewed_status, reviewed_at, purged_at, deleted_at";

  /** Date de soumission : journal de sécurité, il n'y a pas de colonne dédiée. */
  private static final String SUBMITTED_AT =
      "(select max(e.occurred_at) from security_event e where e.practitioner_id = p.id"
          + " and e.kind = 'verification_submitted')";

  private final JdbcClient jdbc;

  AdminVerificationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp t = rs.getTimestamp(column);
    return t == null ? null : t.toInstant();
  }

  private static AccountRow mapAccount(ResultSet rs, int i) throws SQLException {
    return new AccountRow(
        rs.getObject("id", UUID.class),
        rs.getObject("tenant_id", UUID.class),
        rs.getString("email"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("display_name"),
        rs.getString("profession"),
        rs.getString("locale"),
        rs.getString("role"),
        rs.getString("verification_status"),
        instant(rs, "created_at"),
        instant(rs, "email_confirmed_at"),
        instant(rs, "verified_at"),
        rs.getString("phone"),
        rs.getString("professional_number"),
        rs.getString("address_street"),
        rs.getString("address_postal_code"),
        rs.getString("address_city"),
        rs.getBoolean("remote_only"));
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
        instant(rs, "uploaded_at"),
        rs.getString("reviewed_status"),
        instant(rs, "reviewed_at"),
        instant(rs, "purged_at"),
        instant(rs, "deleted_at"));
  }

  // ---- file d'attente

  /**
   * Page de la file par curseur à clé composée (clé de tri tronquée à la milliseconde, id). La
   * direction et l'opérateur viennent de constantes, jamais de l'entrée.
   *
   * @param cursorKey clé de tri du curseur en millisecondes, null pour la première page
   */
  List<QueueRow> queue(
      String status, boolean oldestFirst, Long cursorKey, UUID cursorId, int limit) {
    String op = oldestFirst ? ">" : "<";
    String dir = oldestFirst ? "asc" : "desc";
    StringBuilder sql = new StringBuilder();
    sql.append(
        "select * from (select p.id, p.display_name, p.profession, p.address_city,"
            + " (select count(*) from verification_document d where d.practitioner_id = p.id"
            + " and d.purpose = 'verification' and d.deleted_at is null) as documents_count,"
            + " coalesce((select string_agg(distinct d.type, ',') from verification_document d"
            + " where d.practitioner_id = p.id and d.purpose = 'verification'"
            + " and d.deleted_at is null and d.purged_at is null), '') as present_types, ");
    sql.append(SUBMITTED_AT).append(" as submitted_at, ");
    sql.append(
        "date_trunc('milliseconds', coalesce("
            + SUBMITTED_AT
            + ", p.updated_at)) as sort_key from practitioner p"
            + " where p.role <> 'admin' and p.verification_status = :status) t");
    if (cursorKey != null) {
      sql.append(
          " where (t.sort_key, t.id) "
              + op
              + " (timestamptz 'epoch' + cast(:key as bigint) * interval '1 millisecond', :cid)");
    }
    sql.append(" order by t.sort_key ").append(dir).append(", t.id ").append(dir);
    sql.append(" limit :limit");
    JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).param("status", status);
    if (cursorKey != null) {
      spec = spec.param("key", cursorKey).param("cid", cursorId);
    }
    return spec.param("limit", limit)
        .query(
            (rs, i) ->
                new QueueRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("display_name"),
                    rs.getString("profession"),
                    rs.getString("address_city"),
                    rs.getInt("documents_count"),
                    instant(rs, "submitted_at"),
                    instant(rs, "sort_key"),
                    Arrays.stream(rs.getString("present_types").split(","))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet())))
        .list();
  }

  /** Dossiers under_review avec leur date de soumission, pour le rappel de retard. */
  List<PendingRow> underReview() {
    return jdbc.sql(
            "select p.display_name, p.profession, "
                + SUBMITTED_AT
                + " as submitted_at from practitioner p where p.role <> 'admin'"
                + " and p.verification_status = 'under_review' order by submitted_at, p.id")
        .query(
            (rs, i) ->
                new PendingRow(
                    rs.getString("display_name"),
                    rs.getString("profession"),
                    instant(rs, "submitted_at")))
        .list();
  }

  // ---- dossier

  Optional<AccountRow> findAccount(UUID id) {
    return jdbc.sql("select " + ACCOUNT_COLUMNS + " from practitioner where id = :id")
        .param("id", id)
        .query(AdminVerificationRepository::mapAccount)
        .optional();
  }

  /** Verrouille la ligne du praticien pour sérialiser les décisions concurrentes. */
  Optional<AccountRow> lockAccount(UUID id) {
    return jdbc.sql("select " + ACCOUNT_COLUMNS + " from practitioner where id = :id for update")
        .param("id", id)
        .query(AdminVerificationRepository::mapAccount)
        .optional();
  }

  Optional<Instant> lastSubmittedAt(UUID practitionerId) {
    return jdbc.sql(
            "select max(occurred_at) as submitted_at from security_event"
                + " where practitioner_id = :p and kind = 'verification_submitted'")
        .param("p", practitionerId)
        .query((rs, i) -> instant(rs, "submitted_at"))
        .optional()
        .filter(java.util.Objects::nonNull);
  }

  /** Justificatifs de vérification non retirés par le praticien. */
  List<DocumentRow> activeDocuments(UUID practitionerId) {
    return jdbc.sql(
            "select "
                + DOC_COLUMNS
                + " from verification_document where practitioner_id = :p"
                + " and purpose = 'verification' and deleted_at is null order by uploaded_at, id")
        .param("p", practitionerId)
        .query(AdminVerificationRepository::mapDoc)
        .list();
  }

  /** Justificatif d'un praticien donné, retiré ou non (l'appelant filtre deleted_at). */
  Optional<DocumentRow> findDocument(UUID practitionerId, UUID documentId) {
    return jdbc.sql(
            "select "
                + DOC_COLUMNS
                + " from verification_document where id = :id and practitioner_id = :p")
        .param("id", documentId)
        .param("p", practitionerId)
        .query(AdminVerificationRepository::mapDoc)
        .optional();
  }

  void reviewDocument(UUID documentId, String status, UUID adminId, Instant now) {
    jdbc.sql(
            "update verification_document set reviewed_status = :s, reviewed_at = :now,"
                + " reviewed_by = :a where id = :id")
        .param("s", status)
        .param("now", Timestamp.from(now))
        .param("a", adminId)
        .param("id", documentId)
        .update();
  }

  /** Décisions du plus récent au plus ancien. */
  List<DecisionRow> decisions(UUID practitionerId) {
    return jdbc.sql(
            "select id, kind, message_to_practitioner, internal_note, checklist::text as checklist,"
                + " decided_at, admin_id from verification_decision where practitioner_id = :p"
                + " order by decided_at desc, id")
        .param("p", practitionerId)
        .query(
            (rs, i) ->
                new DecisionRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("kind"),
                    rs.getString("message_to_practitioner"),
                    rs.getString("internal_note"),
                    rs.getString("checklist"),
                    instant(rs, "decided_at"),
                    rs.getObject("admin_id", UUID.class)))
        .list();
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
    jdbc.sql(
            "insert into verification_decision (id, practitioner_id, admin_id, kind, internal_note,"
                + " message_to_practitioner, checklist, related_id, decided_at) values (:id, :p, :a,"
                + " :k, :note, :msg, cast(:checklist as jsonb), :related, :now)")
        .param("id", id)
        .param("p", practitionerId)
        .param("a", adminId)
        .param("k", kind)
        .param("note", internalNote)
        .param("msg", message)
        .param("checklist", checklistJson)
        .param("related", relatedId)
        .param("now", Timestamp.from(now))
        .update();
  }

  void markApproved(UUID practitionerId, UUID adminId, Instant now) {
    jdbc.sql(
            "update practitioner set verification_status = 'approved', verified_at = :now,"
                + " verified_by = :a, updated_at = :now where id = :id")
        .param("now", Timestamp.from(now))
        .param("a", adminId)
        .param("id", practitionerId)
        .update();
  }

  void markNeedsMore(UUID practitionerId, Instant now) {
    jdbc.sql(
            "update practitioner set verification_status = 'needs_more', updated_at = :now"
                + " where id = :id")
        .param("now", Timestamp.from(now))
        .param("id", practitionerId)
        .update();
  }

  void markRejected(UUID practitionerId, Instant now, Instant purgeAfter) {
    jdbc.sql(
            "update practitioner set verification_status = 'rejected', suspended_at = :now,"
                + " purge_after = :purge, updated_at = :now where id = :id")
        .param("now", Timestamp.from(now))
        .param("purge", Timestamp.from(purgeAfter))
        .param("id", practitionerId)
        .update();
  }

  /** Révoque toutes les sessions du praticien ; renvoie leur nombre. */
  int deleteSessions(UUID practitionerId) {
    return jdbc.sql("delete from spring_session where principal_name = :p")
        .param("p", practitionerId.toString())
        .update();
  }

  /** Planifie la purge des fichiers du dossier (décision + 90 jours). */
  void schedulePurge(UUID practitionerId, Instant purgeAfter) {
    jdbc.sql(
            "update verification_document set purge_after = :purge where practitioner_id = :p"
                + " and purpose = 'verification' and deleted_at is null and purged_at is null")
        .param("purge", Timestamp.from(purgeAfter))
        .param("p", practitionerId)
        .update();
  }

  // ---- journal

  void insertAccessLog(UUID documentId, UUID adminId, String ip, Instant now) {
    jdbc.sql(
            "insert into document_access_log (id, document_id, admin_id, ip, accessed_at) values"
                + " (:id, :d, :a, cast(:ip as inet), :now)")
        .param("id", UUID.randomUUID())
        .param("d", documentId)
        .param("a", adminId)
        .param("ip", ip)
        .param("now", Timestamp.from(now))
        .update();
  }

  /** Journal d'administration (G Q29) : security_event avec l'administrateur comme acteur. */
  void insertAdminEvent(
      UUID practitionerId, UUID adminId, String kind, String ip, String detailsJson, Instant now) {
    jdbc.sql(
            "insert into security_event (id, practitioner_id, actor_id, kind, ip, details,"
                + " occurred_at) values (:id, :p, :a, :k, cast(:ip as inet), cast(:details as"
                + " jsonb), :now)")
        .param("id", UUID.randomUUID())
        .param("p", practitionerId)
        .param("a", adminId)
        .param("k", kind)
        .param("ip", ip)
        .param("details", detailsJson)
        .param("now", Timestamp.from(now))
        .update();
  }

  List<Recipient> adminRecipients() {
    return jdbc.sql(
            "select email, locale from practitioner where role = 'admin'"
                + " and verification_status not in ('rejected','suspended','closed','email_pending')"
                + " order by email")
        .query((rs, i) -> new Recipient(rs.getString("email"), rs.getString("locale")))
        .list();
  }
}
