package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Séance ouverte (non clôturée).")
public record OpenSessionDto(
    @Schema(
            description = "Identifiant de l'entrée séance.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID entryId,
    @Schema(
            description = "Début de la séance (occurred_from), UTC ISO 8601.",
            example = "2026-03-14T09:00:00Z")
        Instant startedAt) {}
