package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminCursors.Position;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accès JDBC à la liste et au détail des comptes, aux suspensions et aux recours d'un compte
 * (M1-14b). Ne lit que practitioner, verification_decision, practitioner_appeal et security_event :
 * aucune table patient, clinique, de liens ni d'annuaire.
 */
@Repository
class AdminPractitionerRepository {

  /** Critères de la liste ; un critère null est ignoré. */
  record Filter(String q, String status, String role, String profession) {}

  /** Compte tel que listé : aucune donnée de sécurité autre que l'état de la 2FA. */
  record AccountRow(
      UUID id,
      String displayName,
      String email,
      String profession,
      String city,
      String role,
      String status,
      Instant verifiedAt,
      Instant submittedAt,
      Instant updatedAt,
      boolean twoFactorEnabled,
      Instant createdAt,
      Instant lastLoginAt,
      Instant suspendedAt,
      Instant purgeAt,
      Instant sortKey) {}

  record AppealRow(
      UUID id,
      UUID decisionId,
      Instant submittedAt,
      Instant reviewedAt,
      String outcome,
      String message,
      String responseMessage) {}

  /** Statuts comptés pour les onglets de la liste. */
  static final List<String> COUNTED =
      List.of("approved", "under_review", "needs_more", "suspended", "rejected");

  /** Statuts d'un compte qui ne peut plus se connecter. */
  private static final String INACTIVE = "('suspended','rejected','closed')";

  private static final String SUBMITTED_AT =
      "(select max(e.occurred_at) from security_event e where e.practitioner_id = p.id"
          + " and e.kind = 'verification_submitted')";

  private static final String COLUMNS =
      "p.id, p.display_name, p.email, p.profession, p.address_city, p.role,"
          + " p.verification_status, p.verified_at, "
          + SUBMITTED_AT
          + " as submitted_at, p.updated_at, (p.totp_enabled_at is not null) as totp,"
          + " p.created_at, p.last_login_at, p.suspended_at, p.purge_after,"
          + " date_trunc('milliseconds', p.created_at) as sort_key";

  private final JdbcClient jdbc;

  AdminPractitionerRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp t = rs.getTimestamp(column);
    return t == null ? null : t.toInstant();
  }

  private static AccountRow map(ResultSet rs, int i) throws SQLException {
    return new AccountRow(
        rs.getObject("id", UUID.class),
        rs.getString("display_name"),
        rs.getString("email"),
        rs.getString("profession"),
        rs.getString("address_city"),
        rs.getString("role"),
        rs.getString("verification_status"),
        instant(rs, "verified_at"),
        instant(rs, "submitted_at"),
        instant(rs, "updated_at"),
        rs.getBoolean("totp"),
        instant(rs, "created_at"),
        instant(rs, "last_login_at"),
        instant(rs, "suspended_at"),
        instant(rs, "purge_after"),
        instant(rs, "sort_key"));
  }

  /** Caractère d'échappement des ILIKE de la recherche. */
  private static final String ESC = "!";

  /** Échappe le caractère d'échappement, % et _ pour un ILIKE. */
  private static String like(String q) {
    return "%" + q.replace(ESC, ESC + ESC).replace("%", ESC + "%").replace("_", ESC + "_") + "%";
  }

  private static String where(Filter f, boolean withStatus, Map<String, Object> params) {
    StringBuilder sql = new StringBuilder(" where true");
    if (f.q() != null) {
      sql.append(
          " and (p.display_name ilike :q escape '"
              + ESC
              + "' or p.email ilike :q escape '"
              + ESC
              + "')");
      params.put("q", like(f.q()));
    }
    if (withStatus && f.status() != null) {
      sql.append(" and p.verification_status = :status");
      params.put("status", f.status());
    }
    if (f.role() != null) {
      sql.append(" and p.role = :role");
      params.put("role", f.role());
    }
    if (f.profession() != null) {
      sql.append(" and p.profession = :profession");
      params.put("profession", f.profession());
    }
    return sql.toString();
  }

  /** Page triée par création décroissante, puis identifiant décroissant. */
  List<AccountRow> list(Filter f, Position cursor, int limit) {
    Map<String, Object> params = new HashMap<>();
    StringBuilder sql =
        new StringBuilder("select " + COLUMNS + " from practitioner p" + where(f, true, params));
    if (cursor != null) {
      sql.append(
          " and (date_trunc('milliseconds', p.created_at), p.id) <"
              + " (timestamptz 'epoch' + cast(:key as bigint) * interval '1 millisecond', :cid)");
      params.put("key", cursor.key());
      params.put("cid", cursor.id());
    }
    sql.append(" order by date_trunc('milliseconds', p.created_at) desc, p.id desc limit :limit");
    params.put("limit", limit);
    return jdbc.sql(sql.toString()).params(params).query(AdminPractitionerRepository::map).list();
  }

  /** Nombre de comptes par statut suivi, hors filtre de statut. */
  Map<String, Integer> counts(Filter f) {
    Map<String, Object> params = new HashMap<>();
    String sql =
        "select p.verification_status as s, count(*) as n from practitioner p"
            + where(f, false, params)
            + " group by p.verification_status";
    Map<String, Integer> found = new HashMap<>();
    jdbc.sql(sql)
        .params(params)
        .query(
            (rs, i) -> {
              found.put(rs.getString("s"), rs.getInt("n"));
              return null;
            })
        .list();
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String s : COUNTED) {
      counts.put(s, found.getOrDefault(s, 0));
    }
    return counts;
  }

  Optional<AccountRow> find(UUID id) {
    return jdbc.sql("select " + COLUMNS + " from practitioner p where p.id = :id")
        .param("id", id)
        .query(AdminPractitionerRepository::map)
        .optional();
  }

  List<AppealRow> appeals(UUID practitionerId) {
    return jdbc.sql(
            "select id, decision_id, submitted_at, reviewed_at, outcome, message, response_message"
                + " from practitioner_appeal where practitioner_id = :p"
                + " order by submitted_at desc, id")
        .param("p", practitionerId)
        .query(
            (rs, i) ->
                new AppealRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("decision_id", UUID.class),
                    instant(rs, "submitted_at"),
                    instant(rs, "reviewed_at"),
                    rs.getString("outcome"),
                    rs.getString("message"),
                    rs.getString("response_message")))
        .list();
  }

  // ---- suspension et réactivation

  /**
   * Verrouille tous les administrateurs actifs, par id croissant, et renvoie leurs identifiants.
   * L'ordre unique de verrouillage sérialise deux suspensions croisées d'administrateurs ; la cible
   * exclue, une liste vide signifie qu'elle est le dernier administrateur.
   */
  List<UUID> lockActiveAdmins() {
    return jdbc.sql(
            "select id from practitioner where role = 'admin'"
                + " and verification_status not in "
                + INACTIVE
                + " order by id for update")
        .query((rs, i) -> rs.getObject("id", UUID.class))
        .list();
  }

  void markSuspended(UUID practitionerId, Instant now) {
    jdbc.sql(
            "update practitioner set verification_status = 'suspended', suspended_at = :now,"
                + " updated_at = :now where id = :id")
        .param("now", Timestamp.from(now))
        .param("id", practitionerId)
        .update();
  }

  void markReactivated(UUID practitionerId, String status, Instant now) {
    jdbc.sql(
            "update practitioner set verification_status = :s, suspended_at = null,"
                + " purge_after = null, updated_at = :now where id = :id")
        .param("s", status)
        .param("now", Timestamp.from(now))
        .param("id", practitionerId)
        .update();
  }

  /** Statut que le compte avait avant sa dernière suspension manuelle, lu dans la checklist. */
  Optional<String> previousStatusBeforeSuspension(UUID practitionerId) {
    return jdbc.sql(
            "select checklist->>'previousStatus' from verification_decision"
                + " where practitioner_id = :p and kind = 'suspended'"
                + " order by decided_at desc, id desc limit 1")
        .param("p", practitionerId)
        .query((rs, i) -> rs.getString(1))
        .optional()
        .filter(Objects::nonNull);
  }
}
