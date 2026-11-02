package com.psymate.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Facette par type d'entrée.")
public record EntrySearchTypeFacetDto(
    @Schema(
            description = "Code du type d'entrée.",
            allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
            example = "note")
        String code,
    @Schema(description = "Nombre de résultats exacts de ce type.", example = "7") int count) {}
