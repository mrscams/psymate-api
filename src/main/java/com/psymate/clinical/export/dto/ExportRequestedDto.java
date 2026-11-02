package com.psymate.clinical.export.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 202 : job créé. {@code statusUrl} porte le jeton de suivi (compte sans session). */
@Schema(description = "Réponse 202 : export asynchrone mis en file.")
public record ExportRequestedDto(
    @Schema(description = "Identifiant du job d'export.") UUID jobId,
    @Schema(
            description =
                "URL relative de suivi (GET), qui porte un jeton token valable sans session."
                    + " À interroger jusqu'à ce que status vaille done ou failed.",
            example = "/api/me/export/3f8e2b1a-6c4d-4e57-9a0b-1d2c3e4f5a6b?token=Zm9v")
        String statusUrl) {}
