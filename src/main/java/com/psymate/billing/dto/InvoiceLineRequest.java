package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ligne de facture demandée (création et remplacement des lignes d'un brouillon, L5.3). Une ligne
 * avec {@code sessionEntryId} facture cette séance avec la désignation et le prix donnés ; sans,
 * c'est une ligne libre (100 au plus par facture). Montant en centimes entiers (G Q84) : un nombre
 * décimal ou une chaîne est refusé (INVALID_FORMAT). Tout autre champ (id, position, amount,
 * credited…) est refusé (UNKNOWN_FIELD).
 */
@Schema(
    description =
        "Ligne de facture. Avec sessionEntryId : ligne de la séance (désignation et prix donnés,"
            + " date de la séance) ; sans : ligne libre. Prix en centimes d'euro entiers, négatif"
            + " pour une remise ; un nombre décimal ou une chaîne est refusé (INVALID_FORMAT)."
            + " Tout autre champ est refusé (UNKNOWN_FIELD).")
public record InvoiceLineRequest(
    @Schema(
            description =
                "Séance facturée par la ligne : séance close du patient de la facture, à facturer,"
                    + " écartée ou déjà dans ce brouillon. Absent pour une ligne libre.")
        UUID sessionEntryId,
    @Schema(description = "Prestation du praticien (archivée comprise). Facultative.")
        UUID serviceId,
    @Schema(
            description = "Désignation, obligatoire, 200 caractères au plus. Aucun texte clinique.",
            maxLength = 200,
            example = "Bilan psychologique",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 200)
        String label,
    @Schema(
            description =
                "Date de la ligne (AAAA-MM-JJ), facultative ; pour une ligne de séance, toujours"
                    + " le jour de la séance (la valeur envoyée est ignorée).",
            example = "2026-11-05")
        LocalDate occurredOn,
    @Schema(
            description = "Quantité, de 1 à 999 ; 1 si absente.",
            minimum = "1",
            maximum = "999",
            example = "1")
        @Min(1)
        @Max(999)
        Integer quantity,
    @Schema(
            description =
                "Prix unitaire en centimes d'euro, entier de -10000000 à 10000000 ; négatif pour"
                    + " une remise.",
            minimum = "-10000000",
            maximum = "10000000",
            example = "6000",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(-10_000_000)
        @Max(10_000_000)
        Integer unitPrice) {}
