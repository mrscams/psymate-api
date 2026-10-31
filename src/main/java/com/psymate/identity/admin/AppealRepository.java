package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminCursors.Position;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accès JDBC aux recours des comptes refusés ou suspendus (M1-14b, fiche 27). Ne lit que
 * practitioner_appeal, verification_decision et practitioner : aucune table patient.
 */
@Repository
class AppealRepository {

  /** Décision contestable : refus du dossier ou suspension. */
  record DecisionRef(UUID id, String kind, UUID adminId) {}

  /** Recours existant sur une décision. */
  record ExistingAppeal(UUID id, boolean reviewed) {}

  /** Recours avec la décision contestée et le praticien. */
  record AppealRow(
      UUID id,
      UUID practitionerId,
      String practitionerName,
      UUID decisionId,
      String decisionKind,
      UUID decisionAdminId,
      String message,
      Instant submittedAt,
      Instant reviewedAt,
      UUID reviewedBy,
      String outcome,
      String responseMessage,
      Instant sortKey) {}

  private static final String SELECT =
      "select a.id, a.practitioner_id, p.display_name, a.decision_id, d.kind as decision_kind,"
          + " d.admin_id as decision_admin_id, a.message, a.submitted_at, a.reviewed_at,"
          + " a.reviewed_by, a.outcome, a.response_message,"
          + " date_trunc('milliseconds', a.submitted_at) as sort_key"
          + " from practitioner_appeal a join verification_decision d on d.id = a.decision_id"
          + " join practitioner p on p.id = a.practitioner_id";

  private final JdbcClient jdbc;

  AppealRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static Instant instant(java.sql.ResultSet rs, String column)
      throws java.sql.SQLException {
    Timestamp t = rs.getTimestamp(column);
    return t == null ? null : t.toInstant();
  }

  private static AppealRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
    return new AppealRow(
        rs.getObject("id", UUID.class),
        rs.getObject("practitioner_id", UUID.class),
        rs.getString("display_name"),
        rs.getObject("decision_id", UUID.class),
        rs.getString("decision_kind"),
        rs.getObject("decision_admin_id", UUID.class),
        rs.getString("message"),
        instant(rs, "submitted_at"),
        instant(rs, "reviewed_at"),
        rs.getObject("reviewed_by", UUID.class),
        rs.getString("outcome"),
        rs.getString("response_message"),
        instant(rs, "sort_key"));
  }

  /** Dernière décision de refus ou de suspension du praticien. */
  Optional<DecisionRef> latestContestableDecision(UUID practitionerId) {
    return jdbc.sql(
            "select id, kind, admin_id from verification_decision where practitioner_id = :p"
                + " and kind in ('rejected','suspended') order by decided_at desc, id desc limit 1")
        .param("p", practitionerId)
        .query(
            (rs, i) ->
                new DecisionRef(
                    rs.getObject("id", UUID.class),
                    rs.getString("kind"),
                    rs.getObject("admin_id", UUID.class)))
        .optional();
  }

  Optional<ExistingAppeal> findByDecision(UUID decisionId) {
    return jdbc.sql(
            "select id, reviewed_at is not null as reviewed from practitioner_appeal"
                + " where decision_id = :d")
        .param("d", decisionId)
        .query((rs, i) -> new ExistingAppeal(rs.getObject("id", UUID.class), rs.getBoolean(2)))
        .optional();
  }

  void insert(UUID id, UUID practitionerId, UUID decisionId, String message, Instant now) {
    jdbc.sql(
            "insert into practitioner_appeal (id, practitioner_id, decision_id, message,"
                + " submitted_at) values (:id, :p, :d, :m, :now)")
        .param("id", id)
        .param("p", practitionerId)
        .param("d", decisionId)
        .param("m", message)
        .param("now", Timestamp.from(now))
        .update();
  }

  /**
   * Page de recours. open : non examinés, du plus ancien au plus récent ; decided et all : du plus
   * récent au plus ancien.
   */
  List<AppealRow> page(String status, Position cursor, int limit) {
    boolean oldestFirst = "open".equals(status);
    Map<String, Object> params = new HashMap<>();
    StringBuilder sql = new StringBuilder(SELECT).append(" where true");
    if ("open".equals(status)) {
      sql.append(" and a.reviewed_at is null");
    } else if ("decided".equals(status)) {
      sql.append(" and a.reviewed_at is not null");
    }
    if (cursor != null) {
      sql.append(" and (date_trunc('milliseconds', a.submitted_at), a.id) ")
          .append(oldestFirst ? ">" : "<")
          .append(" (timestamptz 'epoch' + cast(:key as bigint) * interval '1 millisecond', :cid)");
      params.put("key", cursor.key());
      params.put("cid", cursor.id());
    }
    String dir = oldestFirst ? "asc" : "desc";
    sql.append(" order by date_trunc('milliseconds', a.submitted_at) ")
        .append(dir)
        .append(", a.id ")
        .append(dir)
        .append(" limit :limit");
    params.put("limit", limit);
    return jdbc.sql(sql.toString()).params(params).query(AppealRepository::map).list();
  }

  Optional<AppealRow> find(UUID id) {
    return jdbc.sql(SELECT + " where a.id = :id")
        .param("id", id)
        .query(AppealRepository::map)
        .optional();
  }

  void decide(UUID id, UUID adminId, String outcome, String responseMessage, Instant now) {
    jdbc.sql(
            "update practitioner_appeal set reviewed_by = :a, reviewed_at = :now, outcome = :o,"
                + " response_message = :m where id = :id")
        .param("a", adminId)
        .param("now", Timestamp.from(now))
        .param("o", outcome)
        .param("m", responseMessage)
        .param("id", id)
        .update();
  }
}
