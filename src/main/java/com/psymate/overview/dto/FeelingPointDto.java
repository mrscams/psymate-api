package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Un ressenti : une valeur par point.")
public record FeelingPointDto(
    @Schema(
            description = "Identifiant de l'entrée ressenti.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID entryId,
    @Schema(
            description =
                "Jour UTC de placement du point, AAAA-MM-JJ : le jour du ressenti, ou le milieu"
                    + " de la période pour un ressenti imprécis, plafonné à aujourd'hui.",
            example = "2026-03-12")
        LocalDate date,
    @Schema(
            description = "Début du ressenti (occurred_from), UTC ISO 8601.",
            example = "2026-03-12T20:15:00Z")
        Instant at,
    @Schema(description = "Code de l'émotion ressentie.", example = "anxiety") String emotionCode,
    @Schema(
            description =
                "Famille de l'émotion (joy, trust, fear, surprise, sadness, disgust, anger,"
                    + " anticipation) ; null si l'émotion n'est plus au catalogue.",
            example = "fear")
        String family,
    @Schema(
            description = "Intensité du ressenti, de 0 à 10.",
            minimum = "0",
            maximum = "10",
            example = "7")
        int intensity,
    @Schema(
            description =
                "Valence de l'émotion, de -2 (très négative) à 2 (très positive) ; null si"
                    + " inconnue.",
            minimum = "-2",
            maximum = "2",
            example = "-1")
        Integer valence,
    @Schema(
            description = "Identifiant de la séance de rattachement.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID sessionId) {}
