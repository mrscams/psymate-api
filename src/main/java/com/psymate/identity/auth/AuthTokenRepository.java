package com.psymate.identity.auth;

import com.psymate.identity.persistence.AuthToken;
import com.psymate.identity.persistence.AuthTokenJpaRepository;
import com.psymate.identity.persistence.Times;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class AuthTokenRepository {

  private final AuthTokenJpaRepository tokens;

  AuthTokenRepository(AuthTokenJpaRepository tokens) {
    this.tokens = tokens;
  }

  void insert(UUID practitionerId, String kind, String tokenHash, Instant expiresAt, Instant now) {
    tokens.save(new AuthToken(UUID.randomUUID(), practitionerId, kind, tokenHash, expiresAt, now));
  }

  /** Invalide les jetons actifs du même type (une nouvelle demande annule les précédents). */
  void consumeActive(UUID practitionerId, String kind, Instant now) {
    tokens.consumeActive(practitionerId, kind, Times.utc(now));
  }

  /** Consomme atomiquement un jeton valide ; vide si inconnu, déjà consommé ou expiré. */
  Optional<UUID> consume(String kind, String tokenHash, Instant now) {
    return tokens.consume(kind, tokenHash, now);
  }

  /** Nombre de jetons du type créés depuis {@code since} (limites de renvoi), consommés ou non. */
  int countCreatedSince(UUID practitionerId, String kind, Instant since) {
    return (int) tokens.countCreatedSince(practitionerId, kind, Times.utc(since));
  }
}
