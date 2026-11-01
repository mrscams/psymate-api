package com.psymate.identity.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Accès JPA à {@code auth_token} ; la consommation atomique est dans AuthTokenNativeQueries. */
public interface AuthTokenJpaRepository
    extends JpaRepository<AuthToken, UUID>, AuthTokenNativeQueries {

  /** Invalide les jetons actifs du même type (une nouvelle demande annule les précédents). */
  @Transactional
  @Modifying(flushAutomatically = true)
  @Query(
      "update AuthToken t set t.consumedAt = :now where t.practitionerId = :p and t.kind = :k"
          + " and t.consumedAt is null")
  int consumeActive(
      @Param("p") UUID practitionerId, @Param("k") String kind, @Param("now") OffsetDateTime now);

  /** Jetons du type créés depuis {@code since} (limites de renvoi), consommés ou non. */
  @Query(
      "select count(t) from AuthToken t where t.practitionerId = :p and t.kind = :k"
          + " and t.createdAt >= :since")
  long countCreatedSince(
      @Param("p") UUID practitionerId,
      @Param("k") String kind,
      @Param("since") OffsetDateTime since);
}
