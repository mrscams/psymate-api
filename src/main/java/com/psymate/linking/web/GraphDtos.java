package com.psymate.linking.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO REST du graphe (camelCase). {@code authorType} se lit seulement : aucun corps de requête ne
 * l'accepte (A11).
 */
final class GraphDtos {

  private GraphDtos() {}

  /** Nœud : une entrée du patient, avec son degré dans tout le dossier. */
  @Schema(description = "Nœud du graphe : une entrée non archivée du patient.")
  record GraphNodeDto(
      @Schema(
              description = "Identifiant de l'entrée.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID id,
      @Schema(
              description =
                  "Code du type d'entrée : note, event, situation, feeling, session ou"
                      + " medication (référentiel GET /api/vocabulary/entry-types).",
              example = "feeling")
          String type,
      @Schema(
              description = "Titre de l'entrée ; null si elle n'en a pas.",
              example = "Peur au réveil")
          String title,
      @Schema(
              description =
                  "Début de l'occurrence de l'entrée (occurred.from), ISO 8601 UTC ; null si"
                      + " l'entrée n'a pas de date.",
              example = "2026-10-12T07:30:00Z")
          Instant occurred,
      @Schema(
              description = "Intensité de 0 à 10 ; null sauf pour un ressenti (feeling).",
              minimum = "0",
              maximum = "10",
              example = "7")
          Integer intensity,
      @Schema(
              description =
                  "Code de l'émotion (référentiel GET /api/vocabulary/emotions) ; null sauf pour"
                      + " un ressenti (feeling).",
              example = "fear")
          String emotion,
      @Schema(
              description =
                  "Nombre de liens acceptés de l'entrée dans tout le dossier du patient, pas"
                      + " seulement dans le graphe rendu.",
              example = "3")
          int degree,
      @Schema(
              description =
                  "Distance à la racine en nombre de liens : 0 pour la racine, 1 pour tous les"
                      + " nœuds d'un graphe sans racine et pour les membres d'un expand.",
              example = "1")
          int distance,
      @Schema(
              description =
                  "Auteur de l'entrée : toujours practitioner en V1. Lecture seule, jamais accepté"
                      + " en écriture.",
              accessMode = Schema.AccessMode.READ_ONLY,
              example = "practitioner")
          String authorType) {}

  /** Arête : un lien accepté entre deux nœuds. */
  @Schema(description = "Arête du graphe : un lien accepté entre deux nœuds rendus.")
  record GraphEdgeDto(
      @Schema(
              description = "Identifiant du lien.",
              example = "5d2f7a10-8c4b-4e6a-b1d3-7f9e0a2c4b68")
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

  /** Groupe de nœuds de degré 1, à développer par {@code expand} avec son {@code id}. */
  @Schema(
      description =
          "Super-nœud : regroupement de nœuds de degré 1 d'un même type d'entrée reliés au même"
              + " voisin, rendu à la place de ses membres quand le graphe dépasse 150 nœuds.")
  record GraphSuperNodeDto(
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

  /** Voisinage : {@code truncated} dit que des nœuds ont été regroupés ou retirés. */
  @Schema(
      description =
          "Graphe d'un patient : au plus 150 nœuds, leurs arêtes et les groupes de nœuds"
              + " regroupés.")
  record GraphDto(
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

  /** Corps de POST /graph/expand : exactement un de superNodeId et memberIds. */
  @Schema(
      description =
          "Groupe à développer : exactement un de superNodeId et memberIds (422 INVALID_VALUE"
              + " sinon).")
  record ExpandGraphRequest(
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
}
