package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Adresse du cabinet ; les champs sont contrôlés par le service (obligatoires sauf visio). */
@Schema(
    description =
        "Adresse du cabinet. Obligatoire (rue, code postal, ville) sauf si remoteOnly vaut true ;"
            + " contrôlée par le service, erreurs par champ en 422 VALIDATION_FAILED.")
public record Address(
    @Schema(
            description = "Numéro et nom de la rue (200 caractères au maximum).",
            example = "12 rue de la Paix")
        @Size(max = 200)
        String street,
    @Schema(
            description =
                "Code postal français à 5 chiffres (POSTAL_CODE_INVALID sinon ; 20 caractères au"
                    + " maximum acceptés en entrée).",
            example = "75002")
        @Size(max = 20)
        String postalCode,
    @Schema(description = "Ville (100 caractères au maximum).", example = "Paris") @Size(max = 100)
        String city) {}
