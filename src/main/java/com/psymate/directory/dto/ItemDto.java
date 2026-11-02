package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Entrée du vocabulaire de l'annuaire, active ou non.")
public record ItemDto(
    @Schema(
            description = "Type de l'entrée : specialty (spécialité) ou audience (public).",
            allowableValues = {"specialty", "audience"},
            example = "specialty")
        String kind,
    @Schema(description = "Code stable de l'entrée, unique pour le type.", example = "anxiety")
        String code,
    @Schema(description = "Libellé français.", example = "Anxiété") String labelFr,
    @Schema(description = "Libellé anglais ; null s'il n'y en a pas.", example = "Anxiety")
        String labelEn,
    @Schema(description = "Rang d'affichage dans le type, de 0 à 32767.", example = "1")
        int sortOrder,
    @Schema(
            description =
                "true si l'entrée est proposée dans l'annuaire ; false si désactivée (invisible"
                    + " dans GET /api/vocabulary/directory et refusée dans les profils).")
        boolean active,
    @Schema(
            description =
                "Nombre de fiches publiées qui utilisent ce code ; tant qu'il est supérieur à 0,"
                    + " l'entrée ne peut pas être désactivée.",
            example = "3")
        int usedByPublishedProfiles) {}
