package com.psymate.billing.dto;

import com.psymate.billing.model.BillingIssuer;
import com.psymate.billing.model.Invoice;
import com.psymate.billing.model.InvoiceEvent;
import com.psymate.billing.model.InvoiceLine;
import com.psymate.billing.model.InvoiceRecipients;
import com.psymate.billing.model.InvoiceSummaryRow;
import com.psymate.billing.model.InvoiceTotals;
import com.psymate.patients.api.PatientBillingView;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Factures vers DTO REST (L5.3), écrit à la main : la réponse mêle l'entité, le résumé calculé en
 * SQL (statut, retard, sujet, reste dû), l'émetteur (réglages du moment ou instantané), le patient
 * lu par la façade des patients, les lignes et l'historique. Aucun texte clinique.
 */
@Component
public class InvoiceAssembler {

  /** Résumé de liste ; {@code patient} est null si la façade ne le connaît pas (jamais attendu). */
  public InvoiceSummaryDto toSummary(InvoiceSummaryRow row, PatientBillingView patient) {
    return new InvoiceSummaryDto(
        row.id(),
        row.kind(),
        row.number(),
        row.status(),
        row.overdue(),
        row.overdueDays(),
        patientRef(row, patient),
        row.issueDate(),
        row.dueOn(),
        subject(row),
        row.total(),
        row.balanceDue(),
        row.lastPaymentMode(),
        row.creditNoteNumber(),
        row.cancelsNumber());
  }

  /**
   * Facture complète. {@code totals} : recalculés pour un brouillon, figés sinon ; {@code
   * nextNumberPreview} null hors brouillon ; {@code cancels} null hors avoir.
   */
  public InvoiceDto toDto(
      Invoice invoice,
      InvoiceSummaryRow row,
      PatientBillingView patient,
      BillingIssuer issuer,
      InvoiceTotals totals,
      List<InvoiceLine> lines,
      Set<UUID> creditedLineIds,
      List<Invoice> creditNotes,
      Invoice cancels,
      List<InvoiceEvent> history,
      String nextNumberPreview) {
    String patientName = patient == null ? null : InvoiceRecipients.fullName(patient);
    return new InvoiceDto(
        invoice.getId(),
        invoice.getKind(),
        invoice.getNumber(),
        row.status(),
        row.overdue(),
        row.overdueDays(),
        patientRef(row, patient),
        invoice.getIssueDate(),
        row.dueOn(),
        subject(row),
        totals.total(),
        row.balanceDue(),
        row.lastPaymentMode(),
        row.creditNoteNumber(),
        row.cancelsNumber(),
        issuer(issuer),
        new InvoiceRecipientDto(
            invoice.getRecipientName(),
            invoice.getRecipientAddress(),
            patientName != null && !patientName.equals(invoice.getRecipientName())
                ? patientName
                : null),
        lines.stream()
            .map(
                l ->
                    new InvoiceLineDto(
                        l.getId(),
                        l.getPosition(),
                        l.getOccurredOn(),
                        l.getServiceId(),
                        l.getLabel(),
                        l.getQuantity(),
                        l.getUnitPriceCents(),
                        l.getAmountCents(),
                        l.getSessionEntryId(),
                        creditedLineIds.contains(l.getId())))
            .toList(),
        invoice.getDueTerm(),
        invoice.getFooterNote(),
        totals.subtotal(),
        totals.vat(),
        invoice.getPaidCents(),
        invoice.getCreditedCents(),
        invoice.getRefundDueCents(),
        nextNumberPreview,
        List.of(),
        creditNotes.stream()
            .map(
                c ->
                    new InvoiceCreditNoteRefDto(
                        c.getId(), c.getNumber(), c.getTotalCents(), c.getIssueDate()))
            .toList(),
        cancels == null ? null : new InvoiceRefDto(cancels.getId(), cancels.getNumber()),
        history.stream()
            .map(e -> new InvoiceHistoryDto(e.getKind(), e.getAt().toInstant(), e.getDetail()))
            .toList(),
        invoice.getUpdatedAt().toInstant());
  }

  /** Brouillon créé par lot : identifiant, patient, total. */
  public InvoiceBatchItemDto toBatchItem(Invoice invoice, PatientBillingView patient, long total) {
    return new InvoiceBatchItemDto(
        invoice.getId(),
        new BillingPatientRefDto(patient.id(), patient.displayName(), patient.archived()),
        total);
  }

  private static InvoiceIssuerDto issuer(BillingIssuer i) {
    return new InvoiceIssuerDto(
        i.name(),
        i.title(),
        i.professionalNumber(),
        i.siret(),
        i.addressLines(),
        i.displayEmail(),
        i.displayPhone(),
        i.vatMention(),
        i.vatRateBp(),
        i.vatNumber(),
        i.chequePayee(),
        i.acceptedModes(),
        i.bankDetailsShown());
  }

  private static BillingPatientRefDto patientRef(
      InvoiceSummaryRow row, PatientBillingView patient) {
    if (patient == null) {
      return new BillingPatientRefDto(row.patientId(), null, false);
    }
    return new BillingPatientRefDto(
        Objects.requireNonNull(patient.id()), patient.displayName(), patient.archived());
  }

  private static InvoiceSubjectDto subject(InvoiceSummaryRow row) {
    return new InvoiceSubjectDto(row.sessionCount(), row.firstOn(), row.lastOn(), row.label());
  }
}
