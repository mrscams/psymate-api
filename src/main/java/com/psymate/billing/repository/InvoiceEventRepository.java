package com.psymate.billing.repository;

import com.psymate.billing.model.InvoiceEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès JPA à {@code invoice_event} (V18), append-only : insertion et lecture seulement (l'entité
 * est {@code @Immutable}). L'appelant a déjà lu la facture au praticien courant.
 */
public interface InvoiceEventRepository extends JpaRepository<InvoiceEvent, UUID> {

  /** Historique d'une facture, du plus ancien au plus récent. */
  List<InvoiceEvent> findByInvoiceIdOrderByAtAscIdAsc(UUID invoiceId);
}
