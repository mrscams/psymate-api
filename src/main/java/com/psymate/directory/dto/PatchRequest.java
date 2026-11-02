package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Modification partielle : un champ absent reste inchangé ; labelEn vide efface le libellé. */
@Schema(
    description =
        "Modification partielle d'une entrée : un champ absent reste inchangé. Le code et le"
            + " type ne se modifient pas.")
public record PatchRequest(
    @Schema(
            description = "Nouveau libellé français, non vide, 120 caractères au plus.",
            maxLength = 120)
        String labelFr,
    @Schema(
            description =
                "Nouveau libellé anglais, 120 caractères au plus ; chaîne vide : efface le"
                    + " libellé anglais.",
            maxLength = 120)
        String labelEn,
    @Schema(
            description = "Nouveau rang d'affichage dans le type, de 0 à 32767.",
            minimum = "0",
            maximum = "32767")
        Integer sortOrder,
    @Schema(
            description =
                "false pour désactiver l'entrée (409 VOCABULARY_IN_USE si une fiche publiée"
                    + " l'utilise), true pour la réactiver.")
        Boolean active) {}
