package com.psymate.identity.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Secret TOTP chiffré et codes de secours (totp_recovery_code) d'un praticien. */
@Repository
class TotpRepository {

  /**
   * secret : chiffré (null = aucune 2FA) ; enabledAt : null tant que l'activation n'est pas
   * vérifiée.
   */
  record TotpState(byte[] secret, Instant enabledAt) {
    boolean enabled() {
      return secret != null && enabledAt != null;
    }

    boolean pending() {
      return secret != null && enabledAt == null;
    }
  }

  private final JdbcClient jdbc;

  TotpRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  TotpState find(UUID id) {
    return jdbc.sql("select totp_secret_enc, totp_enabled_at from practitioner where id = :id")
        .param("id", id)
        .query(
            (rs, i) -> {
              Timestamp at = rs.getTimestamp("totp_enabled_at");
              return new TotpState(
                  rs.getBytes("totp_secret_enc"), at == null ? null : at.toInstant());
            })
        .optional()
        .orElse(new TotpState(null, null));
  }

  void setPendingSecret(UUID id, byte[] encrypted, Instant now) {
    jdbc.sql(
            "update practitioner set totp_secret_enc = :s, totp_enabled_at = null,"
                + " updated_at = :now where id = :id")
        .param("s", encrypted)
        .param("now", Timestamp.from(now))
        .param("id", id)
        .update();
  }

  void activate(UUID id, Instant now) {
    jdbc.sql(
            "update practitioner set totp_enabled_at = :now, updated_at = :now where id = :id"
                + " and totp_secret_enc is not null")
        .param("now", Timestamp.from(now))
        .param("id", id)
        .update();
  }

  void clear(UUID id, Instant now) {
    jdbc.sql(
            "update practitioner set totp_secret_enc = null, totp_enabled_at = null,"
                + " updated_at = :now where id = :id")
        .param("now", Timestamp.from(now))
        .param("id", id)
        .update();
  }

  /** Les codes de secours restants sont marqués utilisés (rien n'est supprimé). */
  void retireRecoveryCodes(UUID id, Instant now) {
    jdbc.sql(
            "update totp_recovery_code set used_at = :now where practitioner_id = :id"
                + " and used_at is null")
        .param("now", Timestamp.from(now))
        .param("id", id)
        .update();
  }

  void insertRecoveryCode(UUID practitionerId, String hash, Instant now) {
    jdbc.sql(
            "insert into totp_recovery_code (id, practitioner_id, code_hash, created_at)"
                + " values (:id, :p, :h, :now)")
        .param("id", UUID.randomUUID())
        .param("p", practitionerId)
        .param("h", hash)
        .param("now", Timestamp.from(now))
        .update();
  }

  /** Marque le code utilisé ; vrai une seule fois (mise à jour atomique). */
  boolean consumeRecoveryCode(UUID practitionerId, String hash, Instant now) {
    return jdbc.sql(
                "update totp_recovery_code set used_at = :now where practitioner_id = :p"
                    + " and code_hash = :h and used_at is null")
            .param("now", Timestamp.from(now))
            .param("p", practitionerId)
            .param("h", hash)
            .update()
        == 1;
  }
}
