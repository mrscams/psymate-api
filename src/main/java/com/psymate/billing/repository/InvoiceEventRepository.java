package com.psymate.billing.repository;

import com.psymate.billing.model.InvoiceEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Accès JPA à {@code invoice_event} (V18), append-only : insertion et lecture seulement, sans
 * aucune méthode de suppression ni de modification (l'entité est {@code @Immutable} ; G Q95 :
 * l'historique n'est jamais supprimé, seule la cascade de V18 l'emporte avec son brouillon, E9).
 * L'appelant a déjà lu la facture au praticien courant.
 */
public interface InvoiceEventRepository extends Repository<InvoiceEvent, UUID> {

  /** Ajoute un événement à l'historique. */
  InvoiceEvent save(InvoiceEvent event);

  /** Historique d'une facture, du plus ancien au plus récent. */
  List<InvoiceEvent> findByInvoiceIdOrderByAtAscIdAsc(UUID invoiceId);
}
