package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Détail d'un ressenti (type feeling).")
public record EntryFeelingDto(
    @Schema(
            description = "Code de l'émotion, issu du catalogue de vocabulaire du praticien.",
            example = "anxiety")
        String emotionCode,
    @Schema(
            description =
                "Famille de l'émotion d'après le catalogue : joy, trust, fear, surprise, sadness,"
                    + " disgust, anger ou anticipation. Null si elle est inconnue.",
            example = "fear")
        String family,
    @Schema(description = "Intensité du ressenti, de 0 à 10.", minimum = "0", maximum = "10")
        Integer intensity,
    @Schema(
            description =
                "Valence copiée de l'émotion au moment de la saisie. Null si l'émotion n'en a"
                    + " pas. Jamais fournie par l'appelant.")
        Integer valence,
    @Schema(
            description =
                "Activation (arousal) copiée de l'émotion au moment de la saisie. Null si"
                    + " l'émotion n'en a pas. Jamais fournie par l'appelant.")
        Integer arousal) {}
