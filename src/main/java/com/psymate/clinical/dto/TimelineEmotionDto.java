package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Émotion d'un ressenti : code et famille d'après le catalogue (nulle si inconnue). */
@Schema(description = "Émotion d'un ressenti d'après le catalogue de vocabulaire du praticien.")
public record TimelineEmotionDto(
    @Schema(description = "Code de l'émotion.", example = "anxiety") String code,
    @Schema(
            description =
                "Famille de l'émotion d'après le catalogue : joy, trust, fear, surprise, sadness,"
                    + " disgust, anger ou anticipation. Null si elle est inconnue.",
            example = "fear")
        String family) {}
