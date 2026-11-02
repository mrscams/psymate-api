package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Résultat de la révocation des autres sessions.")
public record RevokedSessionsResponse(
    @Schema(
            description =
                "Nombre de sessions des autres appareils qui ont été révoquées (la session"
                    + " courante est conservée).",
            example = "2")
        int revoked) {}
