package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Création : type, code, libellés, rang (absent : après le dernier du type). */
@Schema(description = "Nouvelle entrée du vocabulaire de l'annuaire (spécialité ou public).")
public record CreateRequest(
    @Schema(
            description = "Type de l'entrée : specialty (spécialité) ou audience (public).",
            allowableValues = {"specialty", "audience"},
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "specialty")
        String kind,
    @Schema(
            description =
                "Code stable de l'entrée, unique pour le type : commence par une lettre"
                    + " minuscule, puis minuscules, chiffres et underscores, 2 à 40 caractères."
                    + " Ne change plus ensuite.",
            pattern = "^[a-z][a-z0-9_]{1,39}$",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "sleep_therapy")
        String code,
    @Schema(
            description = "Libellé français, 120 caractères au plus.",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "Thérapie du sommeil")
        String labelFr,
    @Schema(
            description =
                "Libellé anglais, 120 caractères au plus ; vide ou absent : pas de libellé"
                    + " anglais.",
            maxLength = 120,
            example = "Sleep therapy")
        String labelEn,
    @Schema(
            description =
                "Rang d'affichage dans le type, de 0 à 32767 ; absent : après le dernier du"
                    + " type.",
            minimum = "0",
            maximum = "32767",
            example = "23")
        Integer sortOrder) {}
