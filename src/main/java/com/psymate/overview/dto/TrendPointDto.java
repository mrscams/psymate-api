package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Point de la courbe : moyenne glissante sur 7 jours à cette date.")
public record TrendPointDto(
    @Schema(description = "Jour UTC du point, AAAA-MM-JJ.", example = "2026-03-14") LocalDate date,
    @Schema(
            description =
                "Intensité moyenne (0 à 10, deux décimales) des ressentis des 7 derniers jours"
                    + " jusqu'à cette date ; null si aucun ressenti.",
            example = "5.25")
        Double averageIntensity,
    @Schema(
            description =
                "Valence moyenne (-2 à 2, deux décimales) des valences connues sur les 7 jours ;"
                    + " null si aucune. Toujours null tant que les valences des émotions ne sont"
                    + " pas renseignées.",
            example = "-0.5")
        Double valence) {}
