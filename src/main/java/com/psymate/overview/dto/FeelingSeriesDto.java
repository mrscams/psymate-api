package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Série de la courbe : une émotion de la fenêtre.")
public record FeelingSeriesDto(
    @Schema(description = "Code de l'émotion.", example = "anxiety") String emotionCode,
    @Schema(description = "Libellé français.", example = "Anxiété") String label,
    @Schema(description = "Libellé anglais ; null s'il n'existe pas.", example = "Anxiety")
        String labelEn,
    @Schema(
            description = "Famille de l'émotion ; null si elle n'est plus au catalogue.",
            example = "fear")
        String family,
    @Schema(description = "Nombre de ressentis de cette émotion dans la fenêtre.", example = "8")
        int count) {}
