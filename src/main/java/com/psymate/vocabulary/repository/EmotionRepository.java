package com.psymate.vocabulary.repository;

import com.psymate.vocabulary.model.Emotion;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Accès JPA à {@code emotion}. Interne au module. */
public interface EmotionRepository extends JpaRepository<Emotion, String> {

  /** Globales actives et personnalisées actives du praticien : une seule requête, tri en SQL. */
  @Query(
      "select e from Emotion e where e.active = true"
          + " and (e.scope = 'global' or e.practitionerId = :p)"
          + " order by case e.scope when 'global' then 0 else 1 end, e.family,"
          + " e.intensityLevel nulls last, e.labelFr")
  List<Emotion> findActiveFor(@Param("p") UUID practitionerId);

  @Query(
      "select e from Emotion e where e.active = true and e.labelNormalized = :n"
          + " and (e.scope = 'global' or e.practitionerId = :p)"
          + " order by case e.scope when 'global' then 0 else 1 end")
  List<Emotion> findActiveByNormalized(
      @Param("p") UUID practitionerId, @Param("n") String normalized, Limit limit);

  default Optional<Emotion> findFirstActiveByNormalized(UUID practitionerId, String normalized) {
    return findActiveByNormalized(practitionerId, normalized, Limit.of(1)).stream().findFirst();
  }

  /** Libellé déjà pris par une globale ou une custom du praticien (active ou non). */
  @Query(
      "select count(e) > 0 from Emotion e where e.labelNormalized = :n"
          + " and (e.scope = 'global' or e.practitionerId = :p) and e.code <> :x")
  boolean labelTaken(
      @Param("p") UUID practitionerId,
      @Param("n") String normalized,
      @Param("x") String excludingCode);

  /** Custom du praticien (active ou non). */
  @Query(
      "select e from Emotion e where e.code = :c and e.scope = 'custom'"
          + " and e.practitionerId = :p")
  Optional<Emotion> findOwnCustom(@Param("p") UUID practitionerId, @Param("c") String code);

  /** Émotion active utilisable par le praticien (globale ou sienne). */
  @Query(
      "select e from Emotion e where e.code = :c and e.active = true"
          + " and (e.scope = 'global' or e.practitionerId = :p)")
  Optional<Emotion> findUsable(@Param("p") UUID practitionerId, @Param("c") String code);

  /**
   * Charge l'émotion avec un verrou de ligne (fusions concurrentes de la même source). Une seule
   * ligne : aucun ordre de verrouillage à garantir.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from Emotion e where e.code = :c")
  Optional<Emotion> findByCodeForUpdate(@Param("c") String code);
}
