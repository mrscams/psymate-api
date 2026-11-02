package com.psymate.linking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Corps de PATCH /api/links/{id} : le nouveau type du lien. */
@Schema(description = "Nouveau type d'un lien existant ; les extrémités ne changent pas.")
public record ChangeLinkTypeRequest(
    @Schema(
            description =
                "Code du nouveau type de lien, parmi ceux de GET /api/vocabulary/link-types ;"
                    + " il doit être autorisé pour le couple de types des deux entrées et"
                    + " différent du type actuel (sinon 409 LINK_EXISTS).",
            example = "worsens")
        @NotBlank
        String linkType) {}
