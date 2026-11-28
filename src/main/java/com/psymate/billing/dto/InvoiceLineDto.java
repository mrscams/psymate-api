package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

/** Ligne d'une facture (L5.3 {@code lines}). Montants en centimes d'euro entiers (G Q84). */
@Schema(
    description =
        "Ligne de facture. Aucun texte clinique : une date et une désignation. Montants en"
            + " centimes d'euro entiers.")
public record InvoiceLineDto(
    @Schema(description = "Identifiant de la ligne (change quand les lignes sont remplacées).")
        UUID id,
    @Schema(description = "Position, à partir de 1.", example = "1") int position,
    @Schema(description = "Date de la ligne (AAAA-MM-JJ). Null sans date.", example = "2026-11-05")
        LocalDate occurredOn,
    @Schema(description = "Prestation de la ligne. Null sans prestation.") UUID serviceId,
    @Schema(description = "Désignation.", example = "Séance individuelle du 05/11/2026")
        String label,
    @Schema(description = "Quantité, de 1 à 999.", example = "1") int quantity,
    @Schema(
            description = "Prix unitaire en centimes ; négatif pour une remise.",
            format = "int32",
            example = "6000")
        int unitPrice,
    @Schema(
            description = "Montant : quantité × prix unitaire, en centimes.",
            format = "int64",
            example = "6000")
        long amount,
    @Schema(description = "Séance facturée par cette ligne. Null pour une ligne libre.")
        UUID sessionEntryId,
    @Schema(description = "Ligne annulée par un avoir (M15-11).") boolean credited) {}
