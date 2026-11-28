package com.psymate.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Remplacement du profil de facturation d'un patient (PUT, L5.1) : un champ absent ou null est
 * effacé, une chaîne vide aussi. Tout autre champ est refusé (UNKNOWN_FIELD).
 */
@Schema(
    description =
        "Profil de facturation complet : remplace l'existant ; un champ absent, null ou vide est"
            + " effacé. Montant en centimes d'euro entiers : un nombre décimal est refusé"
            + " (INVALID_FORMAT). Tout autre champ est refusé (UNKNOWN_FIELD).")
public record BillingProfileRequest(
    @Schema(
            description = "Tarif en centimes d'euro, entier strictement positif ; null l'efface.",
            minimum = "1",
            example = "5500")
        @Positive
        Integer rate,
    @Schema(description = "Adresse de facturation, 300 caractères au plus.", maxLength = 300)
        @Size(max = 300)
        String addressLines,
    @Schema(description = "Nom du tiers, 200 caractères au plus.", maxLength = 200) @Size(max = 200)
        String thirdPartyName,
    @Schema(description = "Adresse du tiers, 300 caractères au plus.", maxLength = 300)
        @Size(max = 300)
        String thirdPartyAddress) {}
