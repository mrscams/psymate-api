package com.psymate.billing.repository;

import com.psymate.billing.model.BillingCounter;
import com.psymate.billing.model.BillingCounterId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès JPA à {@code billing_counter} (V18, G Q89), filtré par praticien. Lecture (dernier numéro
 * d'une suite, existence d'un numéro attribué, M15-06) et, à l'émission (M15-09), création de la
 * ligne de l'année puis verrou de la ligne jusqu'à la fin de la transaction.
 */
public interface BillingCounterRepository extends JpaRepository<BillingCounter, BillingCounterId> {

  /** Vrai si le praticien a déjà reçu au moins un numéro, toutes natures et années confondues. */
  @Query(
      """
      select count(c) > 0 from BillingCounter c
      where c.id.practitionerId = :practitionerId and c.lastNumber > 0
      """)
  boolean anyNumberIssued(@Param("practitionerId") UUID practitionerId);

  /**
   * Crée la ligne d'une suite à 0 si elle n'existe pas encore (première émission de l'année) ; sans
   * effet sinon. Deux émissions concurrentes ne se heurtent pas sur la clé primaire.
   */
  @Modifying
  @Query(
      value =
          """
          insert into billing_counter (practitioner_id, kind, year, last_number)
          values (:practitionerId, :kind, :year, 0)
          on conflict (practitioner_id, kind, year) do nothing
          """,
      nativeQuery = true)
  int ensureExists(
      @Param("practitionerId") UUID practitionerId,
      @Param("kind") String kind,
      @Param("year") int year);

  /**
   * Ligne d'une suite, verrouillée jusqu'à la fin de la transaction ({@code select … for update}, G
   * Q89) : une seule émission à la fois lit et incrémente le dernier numéro.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from BillingCounter c where c.id = :id")
  Optional<BillingCounter> findForUpdate(@Param("id") BillingCounterId id);
}
