package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Drapeau actif posé à la main sur le dossier.")
public record OverviewFlagDto(
    @Schema(
            description =
                "Nature : watch = à surveiller, risk = risque, shared_care = suivi partagé.",
            allowableValues = {"watch", "risk", "shared_care"},
            example = "watch")
        String kind,
    @Schema(
            description = "Raison saisie ; null si aucune (toujours renseignée pour watch).",
            example = "Troubles du sommeil")
        String reason,
    @Schema(description = "Date-heure de pose, UTC ISO 8601.", example = "2026-03-14T09:30:00Z")
        Instant setAt) {}
