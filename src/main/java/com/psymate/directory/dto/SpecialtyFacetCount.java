package com.psymate.directory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Nombre de résultats pour une spécialité de la liste fermée.")
public record SpecialtyFacetCount(
    @Schema(
            description = "Code de la spécialité, à renvoyer dans le filtre specialties.",
            example = "anxiety")
        String code,
    @Schema(description = "Libellé en français.", example = "Anxiété") String labelFr,
    @Schema(
            description = "Libellé en anglais ; peut être null pour une entrée ajoutée sans.",
            example = "Anxiety")
        String labelEn,
    @Schema(
            description =
                "Nombre de fiches qui auraient ce code seul, tous les autres filtres appliqués"
                    + " sauf specialties.",
            example = "12")
        long count) {}
