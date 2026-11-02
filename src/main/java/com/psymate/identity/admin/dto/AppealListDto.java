package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Page de recours.")
public record AppealListDto(
    @Schema(
            description =
                "Recours de la page : du plus ancien au plus récent pour status=open, du plus"
                    + " récent au plus ancien pour decided et all.")
        List<AppealItemDto> items,
    @Schema(
            description =
                "Curseur opaque à renvoyer dans le paramètre cursor pour la page suivante ; null"
                    + " sur la dernière page.")
        String nextCursor) {}
