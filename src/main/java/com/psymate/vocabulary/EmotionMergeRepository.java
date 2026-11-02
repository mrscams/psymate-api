package com.psymate.vocabulary;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accès JPA à {@code emotion_merge}. Interne au module. */
interface EmotionMergeRepository extends JpaRepository<EmotionMerge, UUID> {

  /**
   * Fusion du praticien chargée avec un verrou de ligne : deux undo concurrents se sérialisent, le
   * second relit {@code undone_at} déjà posé et reçoit MERGE_ALREADY_UNDONE. Remplace l'ancien
   * UPDATE conditionnel ({@code undone_at is null and undo_until > now()}) sans perdre l'atomicité.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from EmotionMerge m where m.id = :id and m.practitionerId = :p")
  Optional<EmotionMerge> findOwnForUpdate(@Param("id") UUID id, @Param("p") UUID practitionerId);

  /** Toutes les fusions du praticien, annulées comprises (résolution, M9-03). */
  List<EmotionMerge> findByPractitionerId(UUID practitionerId);
}
