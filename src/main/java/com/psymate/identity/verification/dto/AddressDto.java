package com.psymate.identity.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description =
        "Adresse du cabinet. En lecture, chaque champ vaut null s'il n'est pas renseigné ; en"
            + " écriture (PATCH /api/me/professional-info), un champ absent ou null est inchangé"
            + " et une chaîne vide l'efface.")
public record AddressDto(
    @Schema(
            description = "Numéro et nom de la rue (200 caractères au maximum) ; null si absent.",
            example = "12 rue de la Paix")
        String street,
    @Schema(
            description = "Code postal (200 caractères au maximum) ; null si absent.",
            example = "75002")
        String postalCode,
    @Schema(description = "Ville (200 caractères au maximum) ; null si absente.", example = "Paris")
        String city) {}
