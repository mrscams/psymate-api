package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Modalités de règlement (PUT des réglages). */
@Schema(
    description =
        "Modalités de règlement. Un champ absent ou null prend sa valeur par défaut : dueTerm"
            + " on_receipt, acceptedModes [transfer, cheque, cash], showBankDetails false,"
            + " chequePayee effacé.")
public record BillingPaymentTermsRequest(
    @Schema(
            description = "on_receipt, days_15 ou days_30 (INVALID_VALUE sinon).",
            allowableValues = {"on_receipt", "days_15", "days_30"},
            example = "on_receipt")
        String dueTerm,
    @Schema(description = "Ordre des chèques, 120 caractères au plus.", maxLength = 120)
        @Size(max = 120)
        String chequePayee,
    @Schema(
            description =
                "Modes acceptés, au moins un, parmi transfer, cheque, cash, card, other"
                    + " (INVALID_VALUE sinon) ; les doublons sont retirés.")
        List<String> acceptedModes,
    @Schema(description = "Afficher les coordonnées bancaires sur les factures.")
        Boolean showBankDetails) {}
