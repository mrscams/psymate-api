package com.psymate.identity.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Implémentation du fragment Spring Data {@link AuthTokenNativeQueries}. */
class AuthTokenNativeQueriesImpl implements AuthTokenNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional
  public Optional<UUID> consume(String kind, String tokenHash, Instant now) {
    List<?> rows =
        em.createNativeQuery(
                "update auth_token set consumed_at = :now where token_hash = :h and kind = :k and"
                    + " consumed_at is null and expires_at > :now returning practitioner_id")
            .setParameter("now", Times.utc(now))
            .setParameter("h", tokenHash)
            .setParameter("k", kind)
            .getResultList();
    return rows.isEmpty() ? Optional.empty() : Optional.of((UUID) rows.get(0));
  }
}
