package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Facettes calculées sur le lot filtré, chacune triée par nombre décroissant ; la facette"
            + " specialties ignore le filtre specialties. Les compteurs de toutes les facettes"
            + " sont exacts jusqu'à 500 fiches examinées ; au-delà, ils portent sur les 500"
            + " premières dans l'ordre du tri (G Q48).")
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
