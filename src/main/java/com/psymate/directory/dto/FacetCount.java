package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Nombre de résultats pour une valeur d'une facette.")
public record FacetCount(
    @Schema(
            description =
                "Valeur de la facette : code de profession, de modalité, de public ou"
                    + " de langue.",
            example = "video")
        String code,
    @Schema(description = "Nombre de fiches du lot filtré qui ont cette valeur.", example = "12")
        long count) {}
