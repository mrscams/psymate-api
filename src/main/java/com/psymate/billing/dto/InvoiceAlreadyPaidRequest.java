package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Paiement reçu avant l'émission ({@code alreadyPaid} de POST /api/invoices/{id}/issue, L5.3) :
 * montant = total de la facture, mode accepté par les réglages ({@code other} toujours), date de
 * réception pas dans le futur (au jour de Europe/Paris).
 */
@Schema(
    description =
        "Paiement du total reçu avant l'émission. Tout autre champ est refusé (UNKNOWN_FIELD).")
public record InvoiceAlreadyPaidRequest(
    @Schema(
            description =
                "Mode : transfer, cheque, cash, card ou other ; un mode accepté par les réglages,"
                    + " other toujours (MODE_NOT_ACCEPTED sinon).",
            allowableValues = {"transfer", "cheque", "cash", "card", "other"},
            example = "transfer",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(regexp = "transfer|cheque|cash|card|other")
        String mode,
    @Schema(
            description = "Date de réception (AAAA-MM-JJ), aujourd'hui au plus tard.",
            example = "2026-11-28",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        LocalDate receivedOn,
    @Schema(
            description = "Référence (numéro de chèque…), 120 caractères au plus. Facultative.",
            maxLength = 120)
        @Size(max = 120)
        String reference) {}
