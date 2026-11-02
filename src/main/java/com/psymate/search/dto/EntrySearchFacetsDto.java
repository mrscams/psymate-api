package com.psymate.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Compteurs sur les résultats exacts, triés par effectif décroissant ; chaque facette"
            + " ignore son propre filtre pour que les puces gardent leur compteur.")
public record EntrySearchFacetsDto(
    @Schema(description = "Répartition par type d'entrée.") List<EntrySearchTypeFacetDto> types,
    @Schema(description = "Répartition par émotion.") List<EntrySearchEmotionFacetDto> emotions,
    @Schema(description = "Répartition par tag.") List<EntrySearchTagFacetDto> tags) {}
