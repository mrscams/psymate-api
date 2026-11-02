package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Ressentis réels de la fenêtre, repères de médicament et jours de séance.")
public record FeelingsDto(
    @Schema(
            description =
                "Exactement un point par ressenti non archivé de la fenêtre, hors phases de vie"
                    + " (précision life_phase), du plus ancien au plus récent.")
        List<FeelingPointDto> points,
    @Schema(
            description =
                "Une série par émotion présente dans la fenêtre, par nombre de ressentis"
                    + " décroissant puis par code.")
        List<FeelingSeriesDto> series,
    @Schema(description = "Repères de médicament (début, fin) de la fenêtre.")
        List<TrendMarkerDto> markers,
    @Schema(
            description =
                "Jours UTC des séances de la fenêtre, croissants, sans doublon (AAAA-MM-JJ).")
        List<LocalDate> ticks) {}
