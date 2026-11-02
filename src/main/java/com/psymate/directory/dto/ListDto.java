package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Entrées du vocabulaire de l'annuaire.")
public record ListDto(
    @Schema(
            description =
                "Entrées, actives ou non, par type puis rang puis libellé français quand les"
                    + " deux types sont listés.")
        List<ItemDto> items) {}
