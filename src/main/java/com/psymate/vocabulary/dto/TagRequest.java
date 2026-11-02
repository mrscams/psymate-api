package com.psymate.vocabulary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Libellé d'un tag à créer ou à substituer au libellé actuel.")
public record TagRequest(
    @Schema(
            description =
                "Libellé du tag ; obligatoire, non vide. Un « # » est ignoré, les espaces de bord"
                    + " sont retirés ; l'unicité se juge sans casse ni accents. Un libellé égal à"
                    + " une émotion visible du praticien est refusé (TAG_IS_EMOTION).",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "Travail")
        String label) {}
