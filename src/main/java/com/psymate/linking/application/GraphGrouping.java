package com.psymate.linking.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Niveau de détail du graphe (A6) : quand le voisinage dépasse la limite, les nœuds de degré 1 d'un
 * même type reliés au même voisin deviennent un super-nœud ; si cela ne suffit pas, on garde les
 * nœuds les plus proches de la racine. Fonction pure, sans Spring ni base.
 */
public final class GraphGrouping {

  /** Nœuds les plus proches d'abord, puis les plus liés, puis les plus récents. */
  private static final Comparator<GraphNode> DETAIL_ORDER =
      Comparator.comparingInt(GraphNode::distance)
          .thenComparing(Comparator.comparingInt(GraphNode::degree).reversed())
          .thenComparing(
              GraphNode::occurred, Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
          .thenComparing(GraphNode::id);

  private GraphGrouping() {}

  /** Ordre de rendu d'un voisinage complet : distance, degré décroissant, date décroissante, id. */
  public static List<GraphNode> detailOrder(List<GraphNode> nodes) {
    return nodes.stream().sorted(DETAIL_ORDER).toList();
  }

  /**
   * Applique le niveau de détail.
   *
   * @param full voisinage complet
   * @param rootId racine, jamais regroupée ni retirée ; nul pour la racine « patient »
   * @param limit nombre maximal de nœuds rendus
   * @return {@code full} inchangé s'il tient dans la limite ; sinon la vue regroupée puis
   *     éventuellement tronquée, avec {@code truncated = true}
   */
  public static GraphView apply(GraphView full, UUID rootId, int limit) {
    if (full.nodes().size() <= limit) {
      return full;
    }
    Map<UUID, GraphNode> byId = new HashMap<>();
    full.nodes().forEach(n -> byId.put(n.id(), n));

    Map<UUID, List<GraphEdge>> incident = new HashMap<>();
    for (GraphEdge e : full.edges()) {
      incident.computeIfAbsent(e.source(), k -> new ArrayList<>()).add(e);
      incident.computeIfAbsent(e.target(), k -> new ArrayList<>()).add(e);
    }

    // Groupes (voisin, type) des nœuds de degré 1 : triés par clé pour un résultat déterministe.
    Map<String, List<GraphNode>> groups = new TreeMap<>();
    Map<String, UUID> hubs = new HashMap<>();
    for (GraphNode n : full.nodes()) {
      if (n.id().equals(rootId) || n.degree() != 1) {
        continue;
      }
      List<GraphEdge> edges = incident.getOrDefault(n.id(), List.of());
      if (edges.size() != 1) {
        continue;
      }
      GraphEdge e = edges.get(0);
      UUID neighbor = e.source().equals(n.id()) ? e.target() : e.source();
      if (!byId.containsKey(neighbor)) {
        continue;
      }
      String key = "sn:" + neighbor + ":" + n.type();
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(n);
      hubs.put(key, neighbor);
    }

    Set<UUID> removed = new HashSet<>();
    List<GraphSuperNode> superNodes = new ArrayList<>();
    groups.forEach(
        (key, members) -> {
          if (members.size() < 2) {
            return;
          }
          members.forEach(m -> removed.add(m.id()));
          superNodes.add(
              new GraphSuperNode(
                  key,
                  members.get(0).type(),
                  members.size(),
                  dominantEmotion(members),
                  hubs.get(key),
                  members.stream().map(GraphNode::id).sorted().toList()));
        });

    List<GraphNode> nodes =
        new ArrayList<>(full.nodes().stream().filter(n -> !removed.contains(n.id())).toList());
    boolean truncated = full.truncated() || !superNodes.isEmpty();

    if (nodes.size() > limit) {
      truncated = true;
      List<GraphNode> sorted = new ArrayList<>(nodes);
      sorted.sort(DETAIL_ORDER);
      List<GraphNode> kept = new ArrayList<>(sorted.subList(0, limit));
      GraphNode root = rootId == null ? null : byId.get(rootId);
      if (root != null && !kept.contains(root)) {
        kept.set(limit - 1, root);
      }
      Set<UUID> keptIds = new HashSet<>();
      kept.forEach(n -> keptIds.add(n.id()));
      nodes.stream().filter(n -> !keptIds.contains(n.id())).forEach(n -> removed.add(n.id()));
      nodes = kept;
      superNodes.removeIf(s -> !keptIds.contains(s.neighborId()));
    }

    List<GraphEdge> edges =
        full.edges().stream()
            .filter(e -> !removed.contains(e.source()) && !removed.contains(e.target()))
            .toList();
    return new GraphView(List.copyOf(nodes), edges, List.copyOf(superNodes), truncated);
  }

  /**
   * Émotion la plus fréquente ; à égalité, la première par ordre alphabétique ; nul sans émotion.
   */
  private static String dominantEmotion(List<GraphNode> members) {
    Map<String, Integer> counts = new TreeMap<>();
    for (GraphNode m : members) {
      if (m.emotion() != null) {
        counts.merge(m.emotion(), 1, Integer::sum);
      }
    }
    String best = null;
    int bestCount = 0;
    for (Map.Entry<String, Integer> e : counts.entrySet()) {
      if (e.getValue() > bestCount) {
        best = e.getKey();
        bestCount = e.getValue();
      }
    }
    return best;
  }
}
