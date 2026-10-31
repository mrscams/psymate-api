package com.psymate.identity.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PractitionerRepository {

  private static final String COLUMNS =
      "id, tenant_id, email, password_hash, first_name, last_name, display_name, locale,"
          + " profession, role, verification_status, preferences::text as preferences";

  private final JdbcClient jdbc;

  PractitionerRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private static PractitionerRow map(ResultSet rs, int i) throws SQLException {
    return new PractitionerRow(
        rs.getObject("id", UUID.class),
        rs.getObject("tenant_id", UUID.class),
        rs.getString("email"),
        rs.getString("password_hash"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("display_name"),
        rs.getString("locale"),
        rs.getString("profession"),
        rs.getString("role"),
        rs.getString("verification_status"),
        rs.getString("preferences"));
  }

  Optional<PractitionerRow> findByEmailNormalized(String emailNormalized) {
    return jdbc.sql("select " + COLUMNS + " from practitioner where email_normalized = :e")
        .param("e", emailNormalized)
        .query(PractitionerRepository::map)
        .optional();
  }

  Optional<PractitionerRow> findById(UUID id) {
    return jdbc.sql("select " + COLUMNS + " from practitioner where id = :id")
        .param("id", id)
        .query(PractitionerRepository::map)
        .optional();
  }

  void insertTenant(UUID id, String name, Instant now) {
    jdbc.sql("insert into tenant (id, name, created_at) values (:id, :name, :now)")
        .param("id", id)
        .param("name", name)
        .param("now", Timestamp.from(now))
        .update();
  }

  void insertPractitioner(PractitionerRow p, String emailNormalized, Instant now) {
    jdbc.sql(
            "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
                + " first_name, last_name, display_name, locale, profession, role,"
                + " verification_status, preferences, created_at, updated_at) values (:id,"
                + " :tenant, :email, :norm, :hash, :fn, :ln, :dn, :locale, :prof, 'practitioner',"
                + " 'email_pending', cast(:prefs as jsonb), :now, :now)")
        .param("id", p.id())
        .param("tenant", p.tenantId())
        .param("email", p.email())
        .param("norm", emailNormalized)
        .param("hash", p.passwordHash())
        .param("fn", p.firstName())
        .param("ln", p.lastName())
        .param("dn", p.displayName())
        .param("locale", p.locale())
        .param("prof", p.profession())
        .param("prefs", p.preferencesJson())
        .param("now", Timestamp.from(now))
        .update();
  }

  void updatePassword(UUID id, String hash, Instant now) {
    jdbc.sql("update practitioner set password_hash = :h, updated_at = :now where id = :id")
        .param("h", hash)
        .param("now", Timestamp.from(now))
        .param("id", id)
        .update();
  }

  void touchLastLogin(UUID id, Instant now) {
    jdbc.sql("update practitioner set last_login_at = :now where id = :id")
        .param("now", Timestamp.from(now))
        .param("id", id)
        .update();
  }

  /** Révoque toutes les sessions serveur du praticien (principal_name = id). */
  void deleteSessions(UUID id) {
    jdbc.sql("delete from spring_session where principal_name = :p")
        .param("p", id.toString())
        .update();
  }
}
