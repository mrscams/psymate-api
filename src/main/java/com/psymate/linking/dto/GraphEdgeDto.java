package com.psymate.linking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Arête : un lien accepté entre deux nœuds. */
@Schema(description = "Arête du graphe : un lien accepté entre deux nœuds rendus.")
public record GraphEdgeDto(
    @Schema(description = "Identifiant du lien.", example = "5d2f7a10-8c4b-4e6a-b1d3-7f9e0a2c4b68")
        UUID id,
    @Schema(
            description = "Identifiant de l'entrée source du lien.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID source,
    @Schema(
            description = "Identifiant de l'entrée cible du lien.",
            example = "c1a7e9d4-2b3f-4a6c-8d5e-0f1a2b3c4d5e")
        UUID target,
    @Schema(
            description =
                "Code du type de lien (triggers, caused_by, worsens, relieves, co_occurs,"
                    + " follows, part_of, treats), référentiel GET /api/vocabulary/link-types.",
            example = "triggers")
        String linkType,
    @Schema(
            description =
                "true si le type est dirigé (flèche de source vers cible) ; false pour un type"
                    + " symétrique comme co_occurs, dont le sens est sans signification.",
            example = "true")
        boolean directed,
    @Schema(
            description =
                "Origine du lien : manual en V1 (ai_suggested et ai_confirmed sont réservés à"
                    + " l'IA, V2).",
            example = "manual")
        String provenance,
    @Schema(
            description =
                "Statut du lien : toujours accepted dans un graphe (les autres statuts n'y"
                    + " figurent pas).",
            example = "accepted")
        String status) {}
