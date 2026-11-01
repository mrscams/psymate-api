package com.psymate.vocabulary.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Émotion exposée aux autres modules : globale (produit) ou personnalisée du praticien courant. */
@Schema(
    description =
        "Émotion utilisable dans un ressenti : globale (catalogue du produit, 29 émotions) ou"
            + " personnalisée par le praticien connecté.")
public record EmotionView(
    @Schema(
            description =
                "Code stable de l'émotion : mot anglais en minuscules pour une globale (anxiety,"
                    + " joy…), « c_ » suivi de 8 caractères hexadécimaux pour une personnalisée.",
            example = "anxiety")
        String code,
    @Schema(
            description =
                "Portée : global = catalogue du produit, custom = créée par le praticien.",
            allowableValues = {"global", "custom"},
            example = "global")
        String scope,
    @Schema(
            description = "Famille de la roue de Plutchik à laquelle l'émotion est rattachée.",
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
            example = "fear")
        String family,
    @Schema(
            description =
                "Niveau d'intensité lexicale Plutchik : 1 faible, 2 moyen, 3 fort. Null pour les"
                    + " ajouts cliniques (anxiety, guilt, shame, emptiness, calm) et pour les"
                    + " émotions personnalisées.",
            minimum = "1",
            maximum = "3",
            example = "2")
        Integer intensityLevel,
    @Schema(description = "Libellé français affiché.", example = "Anxiété") String labelFr,
    @Schema(
            description =
                "Libellé anglais ; null pour une émotion personnalisée (saisie en français"
                    + " seulement).",
            example = "Anxiety")
        String labelEn,
    @Schema(
            description =
                "true si l'émotion peut être choisie. Les listes ne renvoient que des émotions"
                    + " actives ; une personnalisée fusionnée devient inactive jusqu'à l'annulation"
                    + " de la fusion.",
            example = "true")
        boolean active) {}
