package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description =
        "Dépôt d'un recours par un praticien dont le compte est refusé ou suspendu. Public : le"
            + " praticien s'identifie par son e-mail et son mot de passe dans le corps, car il"
            + " n'a pas de session.")
public record AppealRequest(
    @Schema(
            description = "Adresse e-mail du compte (insensible à la casse).",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "claire.martin@example.org")
        String email,
    @Schema(
            description = "Mot de passe du compte.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            accessMode = Schema.AccessMode.WRITE_ONLY)
        String password,
    @Schema(
            description =
                "Code à usage unique de la double authentification (ou code de secours) ;"
                    + " obligatoire si la 2FA est active, sinon la réponse est 202 avec"
                    + " otpRequired. Facultatif sinon.",
            example = "123456",
            accessMode = Schema.AccessMode.WRITE_ONLY)
        String otp,
    @Schema(
            description =
                "Argumentaire du recours, de 20 à 4000 caractères après suppression des blancs"
                    + " de bord. Erreurs 422 : REQUIRED, TOO_SHORT, TOO_LONG.",
            minLength = 20,
            maxLength = 4000,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String message) {}
