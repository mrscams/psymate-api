package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Situation en cours (occurred_to nul).")
public record ActiveSituationDto(
    @Schema(
            description = "Identifiant de l'entrée situation.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID entryId,
    @Schema(
            description = "Titre de la situation ; null si elle n'en a pas.",
            example = "Conflit au travail")
        String title,
    @Schema(
            description = "Début de la situation (occurred_from), UTC ISO 8601.",
            example = "2026-02-01T00:00:00Z")
        Instant since,
    @Schema(description = "Nombre de liens de la situation.", example = "3") int linkCount,
    @Schema(
            description =
                "Ressentis non archivés reliés par un lien accepté, dans un sens ou l'autre.",
            example = "2")
        int linkedFeelingCount,
    @Schema(
            description =
                "Moyenne des intensités (0 à 10, deux décimales) de ces ressentis ; null s'il"
                    + " n'y en a aucun.",
            example = "6.5")
        Double averageIntensity) {}
