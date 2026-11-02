package com.psymate.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Facette par émotion (ressentis).")
public record EntrySearchEmotionFacetDto(
    @Schema(description = "Code de l'émotion.", example = "anxiety") String code,
    @Schema(description = "Libellé français de l'émotion.", example = "Anxiété") String label,
    @Schema(description = "Nombre de résultats exacts portant cette émotion.", example = "3")
        int count) {}
