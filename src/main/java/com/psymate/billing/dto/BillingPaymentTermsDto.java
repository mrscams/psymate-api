package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Modalités de règlement proposées sur les factures (L5.1). */
@Schema(description = "Modalités de règlement proposées par défaut sur chaque facture.")
public record BillingPaymentTermsDto(
    @Schema(
            description =
                "Échéance par défaut : on_receipt (à réception), days_15, days_30 (jours après"
                    + " l'émission).",
            allowableValues = {"on_receipt", "days_15", "days_30"},
            example = "on_receipt")
        String dueTerm,
    @Schema(description = "Ordre des chèques, 120 caractères au plus ; null si non renseigné.")
        String chequePayee,
    @Schema(
            description =
                "Modes de paiement acceptés, sans doublon, parmi transfer, cheque, cash, card,"
                    + " other.")
        List<String> acceptedModes,
    @Schema(description = "Afficher les coordonnées bancaires sur les factures.")
        boolean showBankDetails,
    @Schema(
            description =
                "Quatre derniers caractères de l'IBAN enregistré, null sans IBAN. L'IBAN entier"
                    + " n'est jamais renvoyé.",
            example = "0189")
        String ibanLast4) {}
