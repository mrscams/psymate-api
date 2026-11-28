package com.psymate.billing.repository;

import com.psymate.billing.model.Invoice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès JPA à {@code invoice} (V18, G Q32, Q88) ; liste et résumés calculés en SQL par le fragment
 * {@link InvoiceNativeQueries} (G Q38). Toute lecture est filtrée par praticien : une facture d'un
 * autre praticien est introuvable (404). Seul un brouillon se supprime (E9), par le service, après
 * le contrôle de son état.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, InvoiceNativeQueries {

  /** Facture du praticien. */
  Optional<Invoice> findByIdAndPractitionerId(UUID id, UUID practitionerId);

  /**
   * Facture du praticien, verrouillée jusqu'à la fin de la transaction ({@code select … for
   * update}) : deux modifications, ou une modification et une suppression, d'un même brouillon se
   * suivent (et l'émission de M15-09 les attendra).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select i from Invoice i
      where i.id = :id and i.practitionerId = :practitionerId
      """)
  Optional<Invoice> findForUpdate(
      @Param("id") UUID id, @Param("practitionerId") UUID practitionerId);

  /** Avoirs émis sur une facture du praticien, par date d'émission puis numéro (M15-11). */
  @Query(
      """
      select i from Invoice i
      where i.cancelsInvoiceId = :invoiceId and i.practitionerId = :practitionerId
      order by i.issueDate, i.number
      """)
  List<Invoice> findCreditNotes(
      @Param("invoiceId") UUID invoiceId, @Param("practitionerId") UUID practitionerId);
}
