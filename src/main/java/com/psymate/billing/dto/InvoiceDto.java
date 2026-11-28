package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Facture complète (L5.3 {@code Invoice} = champs de {@code InvoiceSummary} + détail). Montants en
 * centimes d'euro entiers (G Q84). Pour un brouillon, {@code issuer} reflète les réglages du moment
 * et la TVA est calculée avec eux ; pour un document émis, l'instantané et les montants figés.
 */
@Schema(
    description =
        "Facture ou avoir, avec ses lignes, son émetteur, son destinataire et son historique."
            + " Montants en centimes d'euro entiers (6000 = 60,00 €).")
public record InvoiceDto(
    @Schema(description = "Identifiant de la facture.") UUID id,
    @Schema(
            description = "Nature : invoice (facture) ou credit_note (avoir).",
            allowableValues = {"invoice", "credit_note"},
            example = "invoice")
        String kind,
    @Schema(
            description = "Numéro attribué à l'émission. Null pour un brouillon.",
            example = "F-2026-0042")
        String number,
    @Schema(
            description =
                "Statut calculé : draft, due, partial, paid, void ; issued pour un avoir.",
            allowableValues = {"draft", "due", "partial", "paid", "void", "issued"},
            example = "draft")
        String status,
    @Schema(
            description =
                "En retard : facture émise, échéance passée (au jour de Europe/Paris), reste dû"
                    + " positif.")
        boolean overdue,
    @Schema(description = "Jours de retard. Null sans retard.", example = "12") Integer overdueDays,
    @Schema(description = "Patient de la facture.") BillingPatientRefDto patient,
    @Schema(
            description =
                "Date d'émission (AAAA-MM-JJ) ; pour un brouillon, la date d'émission prévue.",
            example = "2026-11-28")
        LocalDate issueDate,
    @Schema(description = "Échéance effective (AAAA-MM-JJ).", example = "2026-11-28")
        LocalDate dueOn,
    @Schema(description = "Ce que la facture couvre.") InvoiceSubjectDto subject,
    @Schema(description = "Total en centimes.", format = "int64", example = "18000") long total,
    @Schema(
            description = "Reste dû en centimes, jamais négatif ; 0 pour un avoir.",
            format = "int64",
            example = "18000")
        long balanceDue,
    @Schema(description = "Mode du dernier paiement non annulé. Null sans paiement.")
        String lastPaymentMode,
    @Schema(description = "Numéro du dernier avoir émis sur cette facture. Null sans avoir.")
        String creditNoteNumber,
    @Schema(description = "Pour un avoir : numéro de la facture annulée. Null sinon.")
        String cancelsNumber,
    @Schema(description = "Émetteur.") InvoiceIssuerDto issuer,
    @Schema(description = "Destinataire.") InvoiceRecipientDto recipient,
    @Schema(description = "Lignes, par position.") List<InvoiceLineDto> lines,
    @Schema(
            description = "Échéance : on_receipt, days_15, days_30 ou date (dueOn choisie).",
            allowableValues = {"on_receipt", "days_15", "days_30", "date"},
            example = "on_receipt")
        String dueTerm,
    @Schema(description = "Mention de bas de page. Null sans mention.") String footerNote,
    @Schema(
            description = "Sous-total (somme des lignes) en centimes.",
            format = "int64",
            example = "18000")
        long subtotal,
    @Schema(
            description = "TVA en centimes, 0 hors régime assujetti.",
            format = "int64",
            example = "0")
        long vat,
    @Schema(description = "Total payé en centimes (M15-10).", format = "int64", example = "0")
        long paid,
    @Schema(
            description = "Total crédité par avoir en centimes (M15-11).",
            format = "int64",
            example = "0")
        long credited,
    @Schema(
            description = "Pour un avoir : montant à rembourser en centimes (M15-11).",
            format = "int64",
            example = "0")
        long refundDue,
    @Schema(
            description =
                "Brouillon : numéro que la facture recevrait si elle était émise maintenant, dans"
                    + " la suite de l'année de sa date d'émission prévue (simple aperçu, attribué"
                    + " à l'émission). Null pour un document émis.",
            example = "F-2026-0043")
        String nextNumberPreview,
    @Schema(description = "Paiements (M15-10) ; vide tant qu'il n'y en a pas.")
        List<BillingPaymentDto> payments,
    @Schema(description = "Avoirs émis sur cette facture (M15-11).")
        List<InvoiceCreditNoteRefDto> creditNotes,
    @Schema(description = "Pour un avoir : la facture annulée. Null sinon.") InvoiceRefDto cancels,
    @Schema(description = "Historique, du plus ancien au plus récent.")
        List<InvoiceHistoryDto> history,
    @Schema(description = "Dernière modification, ISO 8601 UTC.") Instant updatedAt) {}
