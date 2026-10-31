package com.psymate.identity.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AuthTokenRepository {

  private final JdbcClient jdbc;

  AuthTokenRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void insert(UUID practitionerId, String kind, String tokenHash, Instant expiresAt, Instant now) {
    jdbc.sql(
            "insert into auth_token (id, practitioner_id, kind, token_hash, expires_at,"
                + " created_at) values (:id, :p, :k, :h, :exp, :now)")
        .param("id", UUID.randomUUID())
        .param("p", practitionerId)
        .param("k", kind)
        .param("h", tokenHash)
        .param("exp", Timestamp.from(expiresAt))
        .param("now", Timestamp.from(now))
        .update();
  }

  /** Invalide les jetons actifs du même type (une nouvelle demande annule les précédents). */
  void consumeActive(UUID practitionerId, String kind, Instant now) {
    jdbc.sql(
            "update auth_token set consumed_at = :now where practitioner_id = :p and kind = :k"
                + " and consumed_at is null")
        .param("now", Timestamp.from(now))
        .param("p", practitionerId)
        .param("k", kind)
        .update();
  }

  /** Consomme atomiquement un jeton valide ; vide si inconnu, déjà consommé ou expiré. */
  Optional<UUID> consume(String kind, String tokenHash, Instant now) {
    return jdbc.sql(
            "update auth_token set consumed_at = :now where token_hash = :h and kind = :k and"
                + " consumed_at is null and expires_at > :now returning practitioner_id")
        .param("now", Timestamp.from(now))
        .param("h", tokenHash)
        .param("k", kind)
        .query(UUID.class)
        .optional();
  }
}
