package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Page de résultats de la recherche dans l'annuaire public.")
public record SearchResult(
    @Schema(description = "Fiches de la page, au plus 20, dans l'ordre du tri demandé.")
        List<Card> items,
    @Schema(description = "Numéro de la page rendue, à partir de 1.", example = "1") int page,
    @Schema(description = "Taille de page, toujours 20.", example = "20") int pageSize,
    @Schema(
            description =
                "Nombre total de fiches correspondantes (dans la limite de 500 fiches"
                    + " examinées).",
            example = "37")
        long total,
    @Schema(description = "Nombre total de pages ; 0 s'il n'y a aucun résultat.", example = "2")
        int totalPages,
    @Schema(description = "Facettes du lot filtré, toutes pages confondues.") Facets facets) {}
