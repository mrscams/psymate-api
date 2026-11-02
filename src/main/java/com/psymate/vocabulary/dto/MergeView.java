package com.psymate.vocabulary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(
    description = "Fusion d'une émotion personnalisée dans une autre, annulable pendant 30 jours.")
public record MergeView(
    @Schema(
            description = "Identifiant de la fusion, à fournir pour l'annuler.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID id,
    @Schema(
            description = "Code de l'émotion personnalisée absorbée (désormais inactive).",
            example = "c_1a2b3c4d")
        String sourceCode,
    @Schema(description = "Code de l'émotion qui a absorbé la source.", example = "sadness")
        String targetCode,
    @Schema(
            description = "Nombre de ressentis dont l'émotion est passée de la source à la cible.",
            example = "3")
        int affectedCount,
    @Schema(
            description = "Date-heure de la fusion, UTC ISO 8601.",
            example = "2026-03-14T09:30:00Z")
        OffsetDateTime mergedAt,
    @Schema(
            description =
                "Date-heure limite d'annulation (30 jours après la fusion), UTC ISO 8601.",
            example = "2026-04-13T09:30:00Z")
        OffsetDateTime undoUntil,
    @Schema(
            description =
                "Date-heure de l'annulation, UTC ISO 8601 ; null tant que la fusion n'a pas été"
                    + " annulée.",
            example = "2026-03-20T10:00:00Z")
        OffsetDateTime undoneAt) {}
