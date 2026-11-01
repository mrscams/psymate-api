package com.psymate.identity.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accès JPA à {@code security_event} : insertion seulement (table append-only). */
public interface SecurityEventJpaRepository
    extends JpaRepository<SecurityEvent, UUID>, AdminJournalNativeQueries {

  /** Date du dernier événement d'un genre pour un praticien (vide s'il n'y en a aucun). */
  @Query(
      "select max(e.occurredAt) from SecurityEvent e where e.practitionerId = :p"
          + " and e.kind = :kind")
  Optional<OffsetDateTime> lastOccurredAt(
      @Param("p") UUID practitionerId, @Param("kind") String kind);
}
