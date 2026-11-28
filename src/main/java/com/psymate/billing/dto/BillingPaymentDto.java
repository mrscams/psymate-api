package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Paiement d'une facture (L5.4 {@code Payment}, M15-10). Tant que M15-10 n'est pas livrée, la liste
 * {@code payments} d'une facture est vide.
 */
@Schema(description = "Paiement ou remboursement. Montant en centimes d'euro entiers.")
public record BillingPaymentDto(
    @Schema(description = "Identifiant du paiement.") UUID id,
    @Schema(description = "Facture payée (ou avoir remboursé).") UUID invoiceId,
    @Schema(description = "Numéro de la facture.", example = "F-2026-0042") String invoiceNumber,
    @Schema(
            description = "Nature : payment ou refund.",
            allowableValues = {"payment", "refund"})
        String kind,
    @Schema(description = "Mode : cash, cheque, transfer, card ou other.", example = "transfer")
        String mode,
    @Schema(description = "Montant en centimes.", format = "int64", example = "6000") long amount,
    @Schema(description = "Date de réception (AAAA-MM-JJ).") LocalDate receivedOn,
    @Schema(description = "Référence (numéro de chèque…). Null sans référence.") String reference,
    @Schema(description = "Note. Null sans note.") String note,
    @Schema(description = "Instant d'annulation, ISO 8601 UTC. Null si le paiement vaut.")
        Instant cancelledAt) {}
