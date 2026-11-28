package com.psymate.billing.repository;

import com.psymate.billing.model.InvoiceListCriteria;
import com.psymate.billing.model.InvoiceSummaryRow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SQL natif des factures (G Q38 : fragment Spring Data sur l'{@code EntityManager} du module). Le
 * statut exposé, le retard, l'échéance effective, le sujet, le total et le reste dû sont calculés
 * en SQL (L3.2, G Q88), si bien que la liste filtre et pagine sur eux. Ne lit que les tables de
 * billing : {@code invoice}, {@code invoice_line} et {@code payment} (dernier mode de paiement).
 *
 * <p>Règles, identiques à {@code InvoiceTotals} : statut {@code draft} pour un brouillon, {@code
 * issued} pour un avoir, {@code void} pour une facture annulée, sinon {@code paid} (reste dû nul),
 * {@code partial} (payé ou crédité positif) ou {@code due} ; reste dû = {@code max(0, total − payé
 * − crédité)}, 0 pour un avoir ; retard = facture émise, reste dû positif, échéance avant {@code
 * today} ; total d'un brouillon = sous-total plus la TVA du taux des réglages du moment, arrondie
 * demi supérieur ({@code floor(x + 0,5)}).
 */
public interface InvoiceNativeQueries {

  /**
   * Une page de la liste du praticien : brouillons d'abord, puis date d'émission décroissante, puis
   * identifiant ; au plus {@code criteria.limit() + 1} lignes (la dernière dit qu'il reste une
   * page).
   */
  List<InvoiceSummaryRow> summaries(UUID practitionerId, InvoiceListCriteria criteria);

  /** Résumés de ces factures du praticien (les autres sont ignorées), dans l'ordre de la liste. */
  List<InvoiceSummaryRow> summariesOf(
      UUID practitionerId, Collection<UUID> invoiceIds, LocalDate today, Integer draftVatRateBp);

  /** Patients qui ont au moins une facture (brouillon compris) du praticien. */
  Set<UUID> invoicedPatientIds(UUID practitionerId);
}
