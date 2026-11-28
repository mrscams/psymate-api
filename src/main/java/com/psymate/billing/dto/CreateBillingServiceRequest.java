package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Création d'une prestation (L5.1). Tout autre champ est refusé (UNKNOWN_FIELD). */
@Schema(
    description =
        "Nouvelle prestation. Montant en centimes d'euro entiers : un nombre décimal est refusé"
            + " (INVALID_FORMAT). Tout autre champ est refusé (UNKNOWN_FIELD).")
public record CreateBillingServiceRequest(
    @Schema(
            description = "Libellé, obligatoire, 120 caractères au plus.",
            maxLength = 120,
            example = "Séance individuelle",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 120)
        String label,
    @Schema(
            description = "Durée en minutes, de 1 à 600, facultative.",
            minimum = "1",
            maximum = "600",
            example = "50")
        @Min(1)
        @Max(600)
        Integer durationMin,
    @Schema(
            description = "Prix en centimes d'euro, entier strictement positif : 6000 = 60,00 €.",
            minimum = "1",
            example = "6000",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Positive
        Integer price,
    @Schema(
            description =
                "Devient la prestation par défaut (retire le défaut de l'autre). Facultatif : la"
                    + " première prestation créée devient la prestation par défaut de toute"
                    + " façon.")
        Boolean isDefault) {}
