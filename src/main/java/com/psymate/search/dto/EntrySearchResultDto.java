package com.psymate.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Page de résultats d'une recherche : entrées, facettes et requête interprétée. Pagination"
            + " par numéro de page (20 par page), non par curseur.")
public record EntrySearchResultDto(
    @Schema(description = "Requête telle que le serveur l'a interprétée.")
        EntrySearchQueryDto query,
    @Schema(description = "Numéro de la page renvoyée, à partir de 1.", example = "1") int page,
    @Schema(description = "Taille de page, fixe : 20.", example = "20") int pageSize,
    @Schema(
            description =
                "Nombre total de résultats exacts sur toutes les pages, hors résultats"
                    + " approchés.",
            example = "25")
        long total,
    @Schema(description = "Nombre de résultats exacts dans items.", example = "20") int exactCount,
    @Schema(
            description =
                "Nombre de résultats approchés dans items (page 1 seulement, et seulement s'il y"
                    + " a moins de 3 résultats exacts, avec un texte sans exclusion -mot ; 0 sinon).",
            example = "0")
        int approximateCount,
    @Schema(
            description =
                "Résultats de la page : exacts d'abord (par pertinence, puis date décroissante),"
                    + " puis approchés (par similarité). Sans texte : par date décroissante.")
        List<EntrySearchItemDto> items,
    @Schema(description = "Facettes calculées sur les résultats exacts.")
        EntrySearchFacetsDto facets,
    @Schema(description = "Durée de traitement côté serveur, en millisecondes.", example = "12")
        long tookMs) {}
