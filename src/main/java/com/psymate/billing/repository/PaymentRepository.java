package com.psymate.billing.repository;

import com.psymate.billing.model.Payment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Accès JPA à {@code payment} (V18, G Q90) : insertion et lecture seulement, sans aucune méthode de
 * suppression (G Q95 : un paiement n'est jamais supprimé ; une annulation pose {@code
 * cancelled_at}, M15-10). L'appelant a déjà lu la facture au praticien courant.
 */
public interface PaymentRepository extends Repository<Payment, UUID> {

  /** Enregistre un paiement. */
  Payment save(Payment payment);

  /** Paiements d'une facture, du plus ancien au plus récent (date de réception, puis saisie). */
  List<Payment> findByInvoiceIdOrderByReceivedOnAscCreatedAtAscIdAsc(UUID invoiceId);
}
