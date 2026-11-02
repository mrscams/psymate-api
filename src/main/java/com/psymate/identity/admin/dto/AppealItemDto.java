package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
    description =
        "Recours vu par l'administration, avec la décision contestée et l'issue éventuelle.")
public record AppealItemDto(
    @Schema(
            description = "Identifiant du recours.",
            example = "7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b")
        UUID id,
    @Schema(
            description = "Identifiant du praticien qui conteste.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID practitionerId,
    @Schema(description = "Nom d'affichage du praticien.", example = "Claire Martin")
        String practitionerDisplayName,
    @Schema(
            description =
                "Identifiant de la décision contestée (dernier refus ou dernière suspension).",
            example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
        UUID decisionId,
    @Schema(
            description =
                "Nature de la décision contestée : rejected (dossier refusé) ou suspended.",
            allowableValues = {"rejected", "suspended"})
        String decisionKind,
    @Schema(
            description =
                "Administrateur auteur de la décision contestée ; il ne peut pas examiner le"
                    + " recours (409 APPEAL_SELF_REVIEW).",
            example = "5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f")
        UUID decisionAdminId,
    @Schema(description = "Message du praticien (20 à 4000 caractères).") String message,
    @Schema(description = "Date-heure UTC (ISO 8601) du dépôt.", example = "2026-04-03T09:00:00Z")
        Instant submittedAt,
    @Schema(
            description =
                "Date-heure UTC (ISO 8601) de l'examen ; null tant que le recours est ouvert.",
            example = "2026-04-05T14:00:00Z")
        Instant reviewedAt,
    @Schema(
            description =
                "Administrateur qui a examiné le recours ; null tant que le recours est ouvert.")
        UUID reviewedBy,
    @Schema(
            description =
                "Issue : accepted (compte réactivé) ou dismissed (recours écarté) ; null tant que"
                    + " le recours est ouvert.",
            allowableValues = {"accepted", "dismissed"})
        String outcome,
    @Schema(
            description =
                "Réponse de l'administrateur au praticien ; null tant que le recours est"
                    + " ouvert.")
        String responseMessage) {}
