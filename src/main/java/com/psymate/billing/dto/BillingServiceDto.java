package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Prestation du praticien (L5.1). Prix en centimes d'euro entiers (G Q84). */
@Schema(description = "Prestation du praticien. Montant en centimes d'euro entiers.")
public record BillingServiceDto(
    @Schema(description = "Identifiant de la prestation.") UUID id,
    @Schema(description = "Libellé, 120 caractères au plus.", example = "Séance individuelle")
        String label,
    @Schema(
            description = "Durée en minutes, de 1 à 600. Null si non précisée.",
            minimum = "1",
            maximum = "600",
            example = "50")
        Integer durationMin,
    @Schema(
            description =
                "Prix en centimes d'euro (entier strictement positif) : 6000 = 60,00 €. Hors taxes"
                    + " si le praticien est assujetti à la TVA.",
            minimum = "1",
            example = "6000")
        int price,
    @Schema(
            description =
                "Prestation par défaut : son prix sert de tarif quand ni la séance ni le patient"
                    + " n'en ont (G Q87). Une seule par praticien.")
        boolean isDefault) {}
