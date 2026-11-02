package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Entrées postérieures à la dernière séance clôturée.")
public record SinceLastSessionDto(
    @Schema(
            description = "Dernière séance clôturée non archivée ; null s'il n'y en a pas.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID lastSessionId,
    @Schema(
            description =
                "Début de cette séance (occurred_from), UTC ISO 8601 ; null s'il n'y en a pas.",
            example = "2026-03-07T09:00:00Z")
        Instant lastSessionAt,
    @Schema(
            description =
                "20 entrées au plus, de la plus récente à la plus ancienne : non archivées, hors"
                    + " séances, non rattachées à la dernière séance, commençant à son début ou"
                    + " après. Sans séance clôturée : les 20 dernières entrées hors séances.")
        List<SinceLastSessionEntryDto> entries) {}
