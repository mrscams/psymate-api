package com.psymate.vocabulary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Nouveau libellé d'une émotion personnalisée.")
public record RenameEmotionRequest(
    @Schema(
            description =
                "Nouveau libellé français ; obligatoire, non vide, mêmes règles d'unicité qu'à la"
                    + " création.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "Fatigue morale")
        String labelFr) {}
