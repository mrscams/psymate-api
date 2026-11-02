package com.psymate.linking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Corps de POST /graph/expand : exactement un de superNodeId et memberIds. */
@Schema(
    description =
        "Groupe à développer : exactement un de superNodeId et memberIds (422 INVALID_VALUE"
            + " sinon).")
public record ExpandGraphRequest(
    @Schema(
            description =
                "Identifiant d'un super-nœud reçu dans superNodes, de la forme"
                    + " sn:{neighborId}:{type d'entrée}. Exclusif avec memberIds.",
            example = "sn:9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e:feeling")
        String superNodeId,
    @Schema(
            description =
                "Identifiants des entrées à rendre : 1 à 500, sans doublon, toutes non"
                    + " archivées et du patient de l'URL. Exclusif avec superNodeId.")
        List<UUID> memberIds) {}
