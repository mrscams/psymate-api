package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Motif d'une suspension ou d'une réactivation manuelle.")
public record ReasonRequest(
    @Schema(
            description =
                "Motif de l'action, de 20 à 4000 caractères après suppression des blancs de"
                    + " bord ; enregistré au journal (repris dans l'e-mail au praticien pour une"
                    + " suspension). Erreurs 422 :"
                    + " REQUIRED, TOO_SHORT, TOO_LONG.",
            minLength = 20,
            maxLength = 4000,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String reason) {}
