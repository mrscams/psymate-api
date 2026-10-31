package com.psymate.identity.admin.bootstrap;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Accès JDBC du bootstrap : tenant, practitioner et security_event, rien d'autre. */
@Repository
class AdminBootstrapRepository {

  private final JdbcClient jdbc;

  AdminBootstrapRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  boolean emailExists(String emailNormalized) {
    return jdbc.sql("select count(*) from practitioner where email_normalized = :e")
            .param("e", emailNormalized)
            .query(Long.class)
            .single()
        > 0;
  }

  void insertTenant(UUID id, String name, Instant now) {
    jdbc.sql("insert into tenant (id, name, created_at) values (:id, :name, :now)")
        .param("id", id)
        .param("name", name)
        .param("now", Timestamp.from(now))
        .update();
  }

  /**
   * Insère l'administrateur : role admin, approuvé, e-mail confirmé, préférence
   * security.mustChangePassword. La 2FA reste inactive (totp_* NULL, contrainte
   * ck_practitioner_admin_totp respectée).
   */
  void insertAdmin(
      UUID id,
      UUID tenantId,
      String email,
      String passwordHash,
      String lastName,
      String preferencesJson,
      Instant now) {
    jdbc.sql(
            "insert into practitioner (id, tenant_id, email, email_normalized,"
                + " email_confirmed_at, password_hash, first_name, last_name, display_name, locale,"
                + " profession, role, verification_status, verified_at, preferences, created_at,"
                + " updated_at) values (:id, :tenant, :email, :email, :now, :hash, 'Admin', :ln,"
                + " 'Administrateur', 'fr', 'psychologist', 'admin', 'approved', :now,"
                + " cast(:prefs as jsonb), :now, :now)")
        .param("id", id)
        .param("tenant", tenantId)
        .param("email", email)
        .param("now", Timestamp.from(now))
        .param("hash", passwordHash)
        .param("ln", lastName)
        .param("prefs", preferencesJson)
        .update();
  }

  void insertSecurityEvent(UUID practitionerId, String kind, Instant now) {
    jdbc.sql(
            "insert into security_event (id, practitioner_id, kind, ip, details, occurred_at)"
                + " values (:id, :p, :k, null, null, :now)")
        .param("id", UUID.randomUUID())
        .param("p", practitionerId)
        .param("k", kind)
        .param("now", Timestamp.from(now))
        .update();
  }
}
