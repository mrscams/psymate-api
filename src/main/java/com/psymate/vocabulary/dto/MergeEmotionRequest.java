package com.psymate.vocabulary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cible d'une fusion d'émotions.")
public record MergeEmotionRequest(
    @Schema(
            description =
                "Code de l'émotion active qui absorbe la source : une émotion globale ou une autre"
                    + " émotion personnalisée du praticien. Obligatoire, différent de la source.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "sadness")
        String targetCode) {}
