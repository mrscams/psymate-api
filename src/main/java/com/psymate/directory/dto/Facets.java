package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Facettes calculées sur le lot filtré (500 fiches au plus), chacune triée par nombre"
            + " décroissant.")
public record Facets(
    @Schema(description = "Répartition par profession (psychologist, psychiatrist, coach).")
        List<FacetCount> professions,
    @Schema(description = "Répartition par modalité (in_person, video, home).")
        List<FacetCount> modalities,
    @Schema(description = "Répartition par public accueilli.") List<FacetCount> audiences,
    @Schema(description = "Répartition par langue (ISO 639-1).") List<FacetCount> languages) {}
