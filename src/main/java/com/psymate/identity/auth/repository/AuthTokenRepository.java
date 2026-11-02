package com.psymate.identity.auth.repository;

import com.psymate.identity.persistence.AuthToken;
import com.psymate.identity.persistence.AuthTokenJpaRepository;
import com.psymate.shared.time.Timestamps;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AuthTokenRepository {

  private final AuthTokenJpaRepository tokens;

  AuthTokenRepository(AuthTokenJpaRepository tokens) {
    this.tokens = tokens;
  }

  public void insert(
      UUID practitionerId, String kind, String tokenHash, Instant expiresAt, Instant now) {
    tokens.save(new AuthToken(UUID.randomUUID(), practitionerId, kind, tokenHash, expiresAt, now));
  }

  /** Invalide les jetons actifs du même type (une nouvelle demande annule les précédents). */
  public void consumeActive(UUID practitionerId, String kind, Instant now) {
    tokens.consumeActive(practitionerId, kind, Timestamps.utc(now));
  }

  /** Consomme atomiquement un jeton valide ; vide si inconnu, déjà consommé ou expiré. */
  public Optional<UUID> consume(String kind, String tokenHash, Instant now) {
    return tokens.consume(kind, tokenHash, now);
  }

  /** Nombre de jetons du type créés depuis {@code since} (limites de renvoi), consommés ou non. */
  public int countCreatedSince(UUID practitionerId, String kind, Instant since) {
    return (int) tokens.countCreatedSince(practitionerId, kind, Timestamps.utc(since));
  }
}
