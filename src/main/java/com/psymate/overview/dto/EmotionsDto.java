package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Émotions dominantes de la fenêtre.")
public record EmotionsDto(
    @Schema(
            description =
                "5 émotions au plus, par nombre de ressentis décroissant puis par code ; liste"
                    + " vide sans ressenti dans la fenêtre.")
        List<TopEmotionDto> top) {}
