package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Entrée d'une liste fermée de l'annuaire, avec ses libellés fr et en.")
public record Item(
    @Schema(
            description =
                "Code stable à envoyer dans le profil ou dans les filtres de recherche"
                    + " (minuscules et underscores, ou code de langue ISO 639-1).",
            example = "anxiety")
        String code,
    @Schema(description = "Libellé en français.", example = "Anxiété") String labelFr,
    @Schema(
            description = "Libellé en anglais ; peut être null pour une entrée ajoutée sans.",
            example = "Anxiety")
        String labelEn) {}
