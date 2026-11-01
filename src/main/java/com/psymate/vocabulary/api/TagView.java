package com.psymate.vocabulary.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Tag libre d'un praticien. */
@Schema(
    description = "Tag libre du praticien connecté, utilisable sur ses patients et ses entrées.")
public record TagView(
    @Schema(description = "Identifiant du tag.", example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID id,
    @Schema(
            description = "Libellé tel que saisi, sans aucun « # », espaces de bord retirés.",
            example = "Travail")
        String label,
    @Schema(
            description =
                "Libellé normalisé servant à l'unicité et aux recherches : minuscules, sans"
                    + " accents ni « # ».",
            example = "travail")
        String normalizedLabel,
    @Schema(
            description =
                "Compteur d'usages maintenu pour l'autocomplétion ; additionné lors d'une fusion"
                    + " de tags.",
            example = "0")
        int usageCount,
    @Schema(description = "Date-heure de création, UTC ISO 8601.", example = "2026-03-14T09:30:00Z")
        OffsetDateTime createdAt) {}
