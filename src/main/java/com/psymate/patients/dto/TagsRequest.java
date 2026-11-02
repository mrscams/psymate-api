package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Liste complète des tags d'un patient (remplace la liste existante).")
public record TagsRequest(
    @Schema(
            description =
                "Libellés de tags existants du praticien (à créer d'abord par POST /api/tags). Un"
                    + " libellé qui correspond à une émotion est refusé (TAG_IS_EMOTION), un"
                    + " libellé inconnu aussi (TAG_UNKNOWN). Liste vide pour retirer tous les"
                    + " tags ; les doublons sont fusionnés.")
        List<String> tags) {}
