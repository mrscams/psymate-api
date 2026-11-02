package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Émotion dominante et variation par rapport à la fenêtre précédente.")
public record TopEmotionDto(
    @Schema(description = "Code de l'émotion.", example = "anxiety") String code,
    @Schema(description = "Libellé français de l'émotion.", example = "Anxiété") String label,
    @Schema(description = "Nombre de ressentis dans la fenêtre.", example = "8") int count,
    @Schema(
            description = "Nombre de ressentis dans la fenêtre précédente, de même longueur.",
            example = "5")
        int previousCount,
    @Schema(description = "count moins previousCount (négatif si en baisse).", example = "3")
        int delta) {}
