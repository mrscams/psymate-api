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

  /** Coordonnées professionnelles saisies à l'inscription (A10.2). */
  record Contact(
      String phone,
      String professionalNumber,
      String street,
      String postalCode,
      String city,
      boolean remoteOnly) {}

  void insertPractitioner(PractitionerRow p, String emailNormalized, Contact c, Instant now) {
    jdbc.sql(
            "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
                + " first_name, last_name, display_name, locale, profession, role,"
                + " verification_status, preferences, phone, professional_number, address_street,"
                + " address_postal_code, address_city, remote_only, created_at, updated_at) values"
                + " (:id, :tenant, :email, :norm, :hash, :fn, :ln, :dn, :locale, :prof,"
                + " 'practitioner', 'email_pending', cast(:prefs as jsonb), :phone, :pro, :street,"
                + " :postal, :city, :remote, :now, :now)")
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
        .param("phone", c.phone())
        .param("pro", c.professionalNumber())
        .param("street", c.street())
        .param("postal", c.postalCode())
        .param("city", c.city())
        .param("remote", c.remoteOnly())
        .param("now", Timestamp.from(now))
        .update();
  }

  /** Passe email_pending à docs_pending et pose email_confirmed_at (A10.3). */
  void confirmEmail(UUID id, Instant now) {
    jdbc.sql(
            "update practitioner set email_confirmed_at = :now, updated_at = :now,"
                + " verification_status = case when verification_status = 'email_pending'"
                + " then 'docs_pending' else verification_status end where id = :id")
        .param("now", Timestamp.from(now))
        .param("id", id)
        .update();
  }

  void updateEmail(UUID id, String email, String emailNormalized, Instant now) {
    jdbc.sql(
            "update practitioner set email = :e, email_normalized = :n, updated_at = :now"
                + " where id = :id")
        .param("e", email)
        .param("n", emailNormalized)
        .param("now", Timestamp.from(now))
        .param("id", id)
        .update();
  }

  void insertSecurityEvent(UUID practitionerId, String kind, String ip, Instant now) {
    jdbc.sql(
            "insert into security_event (id, practitioner_id, kind, ip, occurred_at) values"
                + " (:id, :p, :k, cast(:ip as inet), :now)")
        .param("id", UUID.randomUUID())
        .param("p", practitionerId)
        .param("k", kind)
        .param("ip", ip)
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
