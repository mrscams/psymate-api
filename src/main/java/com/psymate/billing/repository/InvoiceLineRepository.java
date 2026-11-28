package com.psymate.billing.repository;

import com.psymate.billing.model.InvoiceLine;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Accès JPA à {@code invoice_line} (V18, G Q32). L'appelant a déjà lu la facture au praticien
 * courant : les lignes se lisent par facture. Les lignes d'un brouillon se remplacent en bloc
 * (suppression physique permise par E9 pour un brouillon seulement, contrôlée par le service).
 */
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

  /** Lignes d'une facture, par position. */
  List<InvoiceLine> findByInvoiceIdOrderByPosition(UUID invoiceId);

  /**
   * Supprime toutes les lignes d'un brouillon, tout de suite (requête en bloc, après écriture des
   * changements en attente) : les nouvelles lignes reprennent les positions 1, 2… sans heurter
   * l'unicité {@code (invoice_id, position)}. Réservé à un brouillon verrouillé (E9) ; la condition
   * {@code status = 'draft'} est aussi dans la requête : les lignes d'une facture émise ne partent
   * jamais.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      """
      delete from InvoiceLine l
      where l.invoiceId = :invoiceId
        and l.invoiceId in (select i.id from Invoice i where i.id = :invoiceId and i.status = 'draft')
      """)
  int deleteAllOfDraft(@Param("invoiceId") UUID invoiceId);

  /** Parmi ces lignes, celles qu'un avoir a annulées ({@code source_line_id}, M15-11). */
  @Query(
      """
      select distinct l.sourceLineId from InvoiceLine l
      where l.sourceLineId in :lineIds
      """)
  List<UUID> creditedAmong(@Param("lineIds") Collection<UUID> lineIds);
}
