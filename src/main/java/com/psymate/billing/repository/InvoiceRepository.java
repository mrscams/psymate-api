package com.psymate.billing.repository;

import com.psymate.billing.model.Invoice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès JPA à {@code invoice} (V18, G Q32, Q88) ; liste et résumés calculés en SQL par le fragment
 * {@link InvoiceNativeQueries} (G Q38). Toute lecture est filtrée par praticien : une facture d'un
 * autre praticien est introuvable (404). Seul un brouillon se supprime (E9), par {@link
 * #deleteDraft}, dont la requête porte elle-même la condition {@code status = 'draft'}.
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

  /**
   * Supprime le brouillon du praticien (E9), et lui seulement : la condition {@code status =
   * 'draft'} est dans la requête, si bien qu'une facture émise, annulée ou un avoir n'est jamais
   * supprimé. Lignes et historique partent par la cascade de V18. Écrit d'abord les changements en
   * attente (séances rendues à pending).
   *
   * @return 1 si le brouillon a été supprimé, 0 sinon
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      delete from Invoice i
      where i.id = :id and i.practitionerId = :practitionerId and i.status = 'draft'
      """)
  int deleteDraft(@Param("id") UUID id, @Param("practitionerId") UUID practitionerId);
}
