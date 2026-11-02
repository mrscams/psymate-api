package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Page du journal d'administration, des plus récents aux plus anciens.")
public record JournalDto(
    @Schema(description = "Lignes du journal de la page.") List<JournalItemDto> items,
    @Schema(
            description =
                "Curseur opaque à renvoyer dans le paramètre cursor pour la page suivante ; null"
                    + " sur la dernière page.")
        String nextCursor) {}
