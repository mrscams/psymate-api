package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Résumé d'un recours déposé par le praticien, dans le détail de son compte.")
public record AppealSummaryDto(
    @Schema(
            description = "Identifiant du recours.",
            example = "7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b")
        UUID id,
    @Schema(
            description = "Identifiant de la décision (refus ou suspension) contestée.",
            example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
        UUID decisionId,
    @Schema(
            description = "Date-heure UTC (ISO 8601) du dépôt du recours.",
            example = "2026-04-03T09:00:00Z")
        Instant submittedAt,
    @Schema(
            description =
                "Date-heure UTC (ISO 8601) de l'examen ; null tant que le recours est ouvert.",
            example = "2026-04-05T14:00:00Z")
        Instant reviewedAt,
    @Schema(
            description =
                "Issue : accepted (compte réactivé) ou dismissed (recours écarté) ; null tant que"
                    + " le recours est ouvert.",
            allowableValues = {"accepted", "dismissed"})
        String outcome,
    @Schema(description = "Message du praticien (20 à 4000 caractères).") String message,
    @Schema(
            description =
                "Réponse de l'administrateur au praticien ; null tant que le recours est"
                    + " ouvert.")
        String responseMessage) {}
