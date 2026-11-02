package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
    description =
        "Une version d'une entrée. La liste est ordonnée de la plus récente à la plus ancienne.")
public record EntryVersionDto(
    @Schema(description = "Numéro de la version, à partir de 1.", minimum = "1") int version,
    @Schema(description = "Titre de cette version. Null s'il n'y en a pas.") String title,
    @Schema(description = "Texte de cette version. Null s'il n'y en a pas.") String body,
    @Schema(
            description =
                "Motif de la correction (champ reason du PATCH). Null pour la version 1 ou si"
                    + " aucun motif n'a été donné.",
            example = "précision apportée en séance")
        String reason,
    @Schema(
            description =
                "Provenance de l'entrée. Lecture seule : vaut toujours practitioner en V1.",
            accessMode = Schema.AccessMode.READ_ONLY,
            allowableValues = {"practitioner"},
            example = "practitioner")
        String authorType,
    @Schema(description = "Identifiant du praticien auteur de cette version.") UUID authorId,
    @Schema(
            description = "Instant de création de la version, UTC ISO 8601.",
            example = "2026-10-20T19:05:00Z")
        Instant createdAt) {}
