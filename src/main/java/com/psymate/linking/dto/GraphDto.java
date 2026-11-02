package com.psymate.linking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Voisinage : {@code truncated} dit que des nœuds ont été regroupés ou retirés. */
@Schema(
    description =
        "Graphe d'un patient : au plus 150 nœuds (500 pour un développement par expand), leurs"
            + " arêtes et les groupes de nœuds regroupés.")
public record GraphDto(
    @Schema(
            description =
                "Nœuds rendus, des plus proches de la racine aux plus éloignés, puis par degré"
                    + " décroissant, date de début décroissante et identifiant.")
        List<GraphNodeDto> nodes,
    @Schema(
            description =
                "Liens acceptés dont les deux extrémités sont des nœuds rendus (pour un expand :"
                    + " les liens incidents à au moins un membre).")
        List<GraphEdgeDto> edges,
    @Schema(
            description =
                "Groupes de nœuds regroupés en remplacement de leurs membres ; vide si rien"
                    + " n'a été regroupé, et toujours vide pour un expand.")
        List<GraphSuperNodeDto> superNodes,
    @Schema(
            description =
                "true si le graphe est incomplet : des nœuds ont été regroupés en super-nœuds ou"
                    + " retirés pour tenir en 150 nœuds (500 pour un expand).",
            example = "false")
        boolean truncated) {}
