package com.psymate.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Facette par tag.")
public record EntrySearchTagFacetDto(
    @Schema(description = "Identifiant du tag.", example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID id,
    @Schema(description = "Libellé du tag.", example = "travail") String label,
    @Schema(description = "Nombre de résultats exacts portant ce tag.", example = "5") int count) {}
