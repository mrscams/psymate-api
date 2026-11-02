package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Résultat d'une suspension de compte.")
public record SuspendDto(
    @Schema(
            description = "Identifiant du compte suspendu.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID id,
    @Schema(
            description = "Nouveau statut de vérification, toujours suspended.",
            example = "suspended")
        String verificationStatus,
    @Schema(
            description = "Date-heure UTC (ISO 8601) de la suspension.",
            example = "2026-04-02T10:00:00Z")
        Instant suspendedAt,
    @Schema(
            description = "Identifiant de la décision de suspension enregistrée.",
            example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
        UUID decisionId) {}
