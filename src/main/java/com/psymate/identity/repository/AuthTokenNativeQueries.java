package com.psymate.identity.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Consommation atomique d'un jeton : {@code update ... returning practitioner_id}, gardée native
 * (checklist section 6) pour ne pas réécrire la sémantique RETURNING.
 */
public interface AuthTokenNativeQueries {

  /** Consomme un jeton valide ; vide si inconnu, déjà consommé ou expiré. */
  Optional<UUID> consume(String kind, String tokenHash, Instant now);
}
