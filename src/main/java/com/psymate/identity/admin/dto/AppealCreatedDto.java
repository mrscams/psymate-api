package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Recours enregistré.")
public record AppealCreatedDto(
    @Schema(
            description = "Identifiant du recours créé.",
            example = "7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b")
        UUID id,
    @Schema(description = "Date-heure UTC (ISO 8601) du dépôt.", example = "2026-04-03T09:00:00Z")
        Instant submittedAt,
    @Schema(description = "Statut du recours, toujours open à la création.", example = "open")
        String status) {}
