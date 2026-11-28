package com.psymate.billing.repository;

import com.psymate.billing.model.BillingService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès JPA à {@code billing_service} (V18, G Q32) ; verrou consultatif par le fragment {@link
 * BillingNativeQueries} (G Q38). Toute lecture est filtrée par praticien.
 */
public interface BillingServiceRepository
    extends JpaRepository<BillingService, UUID>, BillingNativeQueries {

  /** Prestation du praticien, archivée ou non. */
  Optional<BillingService> findByIdAndPractitionerId(UUID id, UUID practitionerId);

  /**
   * Prestations non archivées du praticien : la prestation par défaut d'abord, puis par libellé.
   */
  @Query(
      """
      select s from BillingService s
      where s.practitionerId = :practitionerId and s.archivedAt is null
      order by s.isDefault desc, lower(s.label), s.label, s.id
      """)
  List<BillingService> findActive(@Param("practitionerId") UUID practitionerId);

  /** Vrai si le praticien a au moins une prestation non archivée ({@code missing}, M15-06). */
  boolean existsByPractitionerIdAndArchivedAtIsNull(UUID practitionerId);
}
