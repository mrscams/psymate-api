package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description =
        "Détail d'un ressenti. À la création, les deux champs sont obligatoires pour le type"
            + " feeling. En PATCH, un champ absent est conservé.")
public record EntryFeelingRequest(
    @Schema(
            description =
                "Code d'une émotion active du catalogue du praticien. Inconnu : UNKNOWN_EMOTION"
                    + " sur feeling.emotionCode.",
            example = "anxiety")
        String emotionCode,
    @Schema(
            description = "Intensité du ressenti, de 0 à 10 (OUT_OF_RANGE sinon).",
            minimum = "0",
            maximum = "10",
            example = "7")
        Integer intensity) {}
