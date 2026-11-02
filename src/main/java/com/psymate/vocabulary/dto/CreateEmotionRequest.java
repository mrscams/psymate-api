package com.psymate.vocabulary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Création d'une émotion personnalisée.")
public record CreateEmotionRequest(
    @Schema(
            description =
                "Libellé français ; obligatoire, non vide. Doit être distinct, sans casse ni"
                    + " accents, de tout libellé d'émotion globale et de ses autres émotions"
                    + " personnalisées (409 EMOTION_LABEL_EXISTS sinon).",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "Lassitude")
        String labelFr,
    @Schema(
            description = "Famille de la roue de Plutchik où ranger l'émotion ; obligatoire.",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {
              "joy",
              "trust",
              "fear",
              "surprise",
              "sadness",
              "disgust",
              "anger",
              "anticipation"
            },
            example = "sadness")
        String family) {}
