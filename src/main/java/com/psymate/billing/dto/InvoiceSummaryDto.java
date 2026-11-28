package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Facture dans la liste (L5.3 {@code InvoiceSummary}). Montants en centimes d'euro entiers (G Q84)
 * ; statut calculé (G Q88).
 */
@Schema(
    description =
        "Facture ou avoir dans la liste. Montants en centimes d'euro entiers (6000 = 60,00 €).")
public record InvoiceSummaryDto(
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
                "Statut calculé : draft (brouillon), due (à payer), partial (en partie payée ou"
                    + " créditée), paid (soldée), void (annulée par un avoir) ; issued pour un"
                    + " avoir.",
            allowableValues = {"draft", "due", "partial", "paid", "void", "issued"},
            example = "due")
        String status,
    @Schema(
            description =
                "En retard : facture émise dont l'échéance est passée (au jour de Europe/Paris)"
                    + " et dont le reste dû est positif.")
        boolean overdue,
    @Schema(
            description = "Jours de retard (aujourd'hui moins l'échéance). Null sans retard.",
            example = "12")
        Integer overdueDays,
    @Schema(description = "Patient de la facture.") BillingPatientRefDto patient,
    @Schema(
            description =
                "Date d'émission (AAAA-MM-JJ) ; pour un brouillon, la date d'émission prévue.",
            example = "2026-11-28")
        LocalDate issueDate,
    @Schema(
            description =
                "Échéance effective (AAAA-MM-JJ) : la date choisie, sinon la date d'émission plus"
                    + " le délai (0, 15 ou 30 jours).",
            example = "2026-12-13")
        LocalDate dueOn,
    @Schema(description = "Ce que la facture couvre.") InvoiceSubjectDto subject,
    @Schema(
            description =
                "Total en centimes (sous-total plus TVA). Pour un brouillon, TVA calculée avec"
                    + " les réglages du moment.",
            format = "int64",
            example = "18000")
        long total,
    @Schema(
            description =
                "Reste dû en centimes : total moins payé moins crédité, jamais négatif ; 0 pour"
                    + " un avoir.",
            format = "int64",
            example = "18000")
        long balanceDue,
    @Schema(
            description = "Mode du dernier paiement non annulé (M15-10). Null sans paiement.",
            example = "transfer")
        String lastPaymentMode,
    @Schema(
            description = "Numéro du dernier avoir émis sur cette facture. Null sans avoir.",
            example = "AV-2026-0003")
        String creditNoteNumber,
    @Schema(
            description = "Pour un avoir : numéro de la facture qu'il annule. Null sinon.",
            example = "F-2026-0042")
        String cancelsNumber) {}
