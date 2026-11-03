package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Facettes calculées sur le lot filtré (500 fiches au plus), chacune triée par nombre"
            + " décroissant ; la facette specialties ignore le filtre specialties.")
public record Facets(
    @Schema(description = "Répartition par profession (psychologist, psychiatrist, coach).")
        List<FacetCount> professions,
    @Schema(
            description =
                "Répartition par spécialité active de la liste fermée, avec libellés. Calculée"
                    + " sans le filtre specialties (les autres filtres s'appliquent) : le"
                    + " compteur d'un code est le nombre de résultats qu'il donnerait seul.")
        List<SpecialtyFacetCount> specialties,
    @Schema(description = "Répartition par modalité (in_person, video, home).")
        List<FacetCount> modalities,
    @Schema(description = "Répartition par public accueilli.") List<FacetCount> audiences,
    @Schema(description = "Répartition par langue (ISO 639-1).") List<FacetCount> languages) {}
