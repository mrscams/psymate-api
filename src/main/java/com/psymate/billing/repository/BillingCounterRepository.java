package com.psymate.billing.repository;

import com.psymate.billing.model.BillingCounter;
import com.psymate.billing.model.BillingCounterId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès JPA à {@code billing_counter} (V18, G Q89), filtré par praticien. M15-06 lit seulement :
 * dernier numéro d'une suite, existence d'un numéro attribué.
 */
public interface BillingCounterRepository extends JpaRepository<BillingCounter, BillingCounterId> {

  /** Vrai si le praticien a déjà reçu au moins un numéro, toutes natures et années confondues. */
  @Query(
      """
      select count(c) > 0 from BillingCounter c
      where c.id.practitionerId = :practitionerId and c.lastNumber > 0
      """)
  boolean anyNumberIssued(@Param("practitionerId") UUID practitionerId);
}
