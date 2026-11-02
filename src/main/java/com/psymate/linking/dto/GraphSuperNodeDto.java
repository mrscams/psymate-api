package com.psymate.linking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Groupe de nœuds de degré 1, à développer par {@code expand} avec son {@code id}. */
@Schema(
    description =
        "Super-nœud : regroupement de nœuds de degré 1 d'un même type d'entrée reliés au même"
            + " voisin, rendu à la place de ses membres quand le graphe dépasse 150 nœuds.")
public record GraphSuperNodeDto(
    @Schema(
            description =
                "Identifiant du groupe, de la forme sn:{neighborId}:{type d'entrée} ; à renvoyer"
                    + " tel quel dans superNodeId de POST /api/patients/{id}/graph/expand.",
            example = "sn:9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e:feeling")
        String id,
    @Schema(description = "Code du type d'entrée commun à tous les membres.", example = "feeling")
        String type,
    @Schema(description = "Nombre de nœuds regroupés (au moins 2).", example = "12") int count,
    @Schema(
            description =
                "Code de l'émotion la plus fréquente parmi les membres (ordre alphabétique à"
                    + " égalité) ; null si aucun membre n'a d'émotion.",
            example = "fear")
        String dominantEmotion,
    @Schema(
            description =
                "Identifiant de l'entrée voisine, rendue dans nodes, à laquelle le"
                    + " groupe est relié.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID neighborId,
    @Schema(description = "Identifiants des entrées regroupées, triés par ordre croissant.")
        List<UUID> memberIds) {}
