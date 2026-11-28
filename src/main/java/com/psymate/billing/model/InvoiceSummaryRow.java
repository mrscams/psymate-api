package com.psymate.billing.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Résumé d'une facture tel que la requête de liste le calcule en SQL ({@code InvoiceNativeQueries})
 * : statut exposé, retard, échéance effective, sujet, total et reste dû (L5.3 {@code
 * InvoiceSummary}). Le nom affiché du patient n'y est pas : il vient de la façade des patients.
 *
 * @param status draft, due, partial, paid, void (facture) ou issued (avoir)
 * @param overdueDays jours de retard (aujourd'hui moins l'échéance), null sans retard
 * @param dueOn échéance effective (date choisie, ou date d'émission plus le délai)
 * @param label désignation de la première ligne quand la facture n'a aucune ligne de séance
 * @param total total en centimes (d'un brouillon : TVA recalculée avec les réglages du moment)
 * @param balanceDue reste dû en centimes, jamais négatif (0 pour un avoir)
 */
public record InvoiceSummaryRow(
    UUID id,
    String kind,
    String number,
    String status,
    boolean overdue,
    Integer overdueDays,
    UUID patientId,
    LocalDate issueDate,
    LocalDate dueOn,
    int sessionCount,
    LocalDate firstOn,
    LocalDate lastOn,
    String label,
    long total,
    long balanceDue,
    String lastPaymentMode,
    String creditNoteNumber,
    String cancelsNumber) {

  /** Rang de tri : les brouillons d'abord (0), puis le reste (1). */
  public int rank() {
    return Invoice.DRAFT.equals(status) ? 0 : 1;
  }
}
