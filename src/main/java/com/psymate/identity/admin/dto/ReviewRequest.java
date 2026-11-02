package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Qualification d'un justificatif par l'administration.")
public record ReviewRequest(
    @Schema(
            description =
                "Nouvel état du justificatif : pending (à examiner), conform ou non_conform ;"
                    + " toute autre valeur donne 422 INVALID_REVIEWED_STATUS.",
            allowableValues = {"pending", "conform", "non_conform"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String reviewedStatus) {}
