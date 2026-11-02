package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Courbe d'intensité et repères de la fenêtre.")
public record TrendDto(
    @Schema(
            description =
                "Points de la courbe, dates croissantes, la dernière étant aujourd'hui : un par"
                    + " jour pour 30d et 90d, un par semaine pour 12m ; 90 au plus.")
        List<TrendPointDto> points,
    @Schema(
            description =
                "Séances et débuts/fins de médicament dont le jour tombe dans la fenêtre, par"
                    + " date croissante.")
        List<TrendMarkerDto> markers) {}
