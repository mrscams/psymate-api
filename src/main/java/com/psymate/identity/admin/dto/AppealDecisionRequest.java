package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Décision d'un administrateur sur un recours ouvert.")
public record AppealDecisionRequest(
    @Schema(
            description =
                "Issue : accepted (le compte est réactivé) ou dismissed (le compte reste"
                    + " inchangé). Autre valeur : 422 INVALID_OUTCOME.",
            allowableValues = {"accepted", "dismissed"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String outcome,
    @Schema(
            description =
                "Réponse transmise au praticien par e-mail, obligatoire (422 REQUIRED), 4000"
                    + " caractères au plus (422 TOO_LONG).",
            maxLength = 4000,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String messageToPractitioner) {}
