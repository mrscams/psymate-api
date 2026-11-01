package com.psymate.linking.web;

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
  record GraphNodeDto(
      UUID id,
      String type,
      String title,
      Instant occurred,
      Integer intensity,
      String emotion,
      int degree,
      int distance,
      String authorType) {}

  /** Arête : un lien accepté entre deux nœuds. */
  record GraphEdgeDto(
      UUID id,
      UUID source,
      UUID target,
      String linkType,
      boolean directed,
      String provenance,
      String status) {}

  /** Groupe de nœuds de degré 1, à développer par {@code expand} avec son {@code id}. */
  record GraphSuperNodeDto(
      String id,
      String type,
      int count,
      String dominantEmotion,
      UUID neighborId,
      List<UUID> memberIds) {}

  /** Voisinage : {@code truncated} dit que des nœuds ont été regroupés ou retirés. */
  record GraphDto(
      List<GraphNodeDto> nodes,
      List<GraphEdgeDto> edges,
      List<GraphSuperNodeDto> superNodes,
      boolean truncated) {}

  /** Corps de POST /graph/expand : exactement un de superNodeId et memberIds. */
  record ExpandGraphRequest(String superNodeId, List<UUID> memberIds) {}
}
