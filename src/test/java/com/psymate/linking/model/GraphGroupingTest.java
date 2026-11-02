package com.psymate.linking.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

/** Niveau de détail du graphe (A6) : fonction pure, sans Spring ni base. */
class GraphGroupingTest {

  private static final int LIMIT = 150;
  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static GraphNode node(
      UUID id, String type, String emotion, int degree, int distance, int dayOffset) {
    return new GraphNode(
        id,
        type,
        "Titre",
        T0.plusSeconds(86_400L * dayOffset),
        emotion == null ? null : 5,
        emotion,
        degree,
        distance,
        "practitioner");
  }

  private static GraphEdge edge(UUID source, UUID target) {
    return new GraphEdge(UUID.randomUUID(), source, target, "triggers", true, "manual", "accepted");
  }

  private static UUID id(int n) {
    return new UUID(0, 1000L + n);
  }

  private static GraphView view(List<GraphNode> nodes, List<GraphEdge> edges) {
    return new GraphView(nodes, edges, List.of(), false);
  }

  /** Une situation racine et {@code count} ressentis de degré 1 qui lui sont reliés. */
  private static GraphView star(int count, IntFunction<String> emotion) {
    List<GraphNode> nodes = new ArrayList<>();
    List<GraphEdge> edges = new ArrayList<>();
    nodes.add(node(ROOT, "situation", null, count, 0, 0));
    for (int i = 0; i < count; i++) {
      nodes.add(node(id(i), "feeling", emotion.apply(i), 1, 1, i));
      edges.add(edge(ROOT, id(i)));
    }
    return view(nodes, edges);
  }

  @Test
  void belowTheLimitTheViewIsUnchanged() {
    GraphView full = star(LIMIT - 10, i -> "anxiety");

    GraphView out = GraphGrouping.apply(full, ROOT, LIMIT);

    assertThat(out).isSameAs(full);
    assertThat(out.truncated()).isFalse();
    assertThat(out.superNodes()).isEmpty();
  }

  @Test
  void exactlyTheLimitIsUnchanged() {
    GraphView full = star(LIMIT - 1, i -> null);
    assertThat(full.nodes()).hasSize(LIMIT);

    assertThat(GraphGrouping.apply(full, ROOT, LIMIT)).isSameAs(full);
  }

  @Test
  void twoHundredDegreeOneFeelingsBecomeOneSuperNode() {
    // 120 anxiety, 80 guilt : l anxiete domine
    GraphView full = star(200, i -> i < 120 ? "anxiety" : "guilt");

    GraphView out = GraphGrouping.apply(full, ROOT, LIMIT);

    assertThat(out.nodes()).extracting(GraphNode::id).containsExactly(ROOT);
    assertThat(out.edges()).isEmpty();
    assertThat(out.truncated()).isTrue();
    assertThat(out.superNodes()).hasSize(1);
    GraphSuperNode sn = out.superNodes().get(0);
    assertThat(sn.id()).isEqualTo("sn:" + ROOT + ":feeling");
    assertThat(sn.type()).isEqualTo("feeling");
    assertThat(sn.count()).isEqualTo(200);
    assertThat(sn.dominantEmotion()).isEqualTo("anxiety");
    assertThat(sn.neighborId()).isEqualTo(ROOT);
    assertThat(sn.memberIds()).hasSize(200).doesNotHaveDuplicates().isSorted();
  }

  @Test
  void memberIdsAreSortedWhateverTheInputOrder() {
    GraphView full = star(200, i -> "calm");
    List<GraphNode> shuffled = new ArrayList<>(full.nodes());
    Collections.reverse(shuffled);

    GraphView out = GraphGrouping.apply(view(shuffled, full.edges()), ROOT, LIMIT);

    assertThat(out.superNodes().get(0).memberIds()).isSorted();
  }

  @Test
  void dominantEmotionTiesGoToTheAlphabeticalFirstAndIsNullWithoutEmotion() {
    GraphView tie = star(200, i -> i % 2 == 0 ? "guilt" : "anxiety");
    assertThat(GraphGrouping.apply(tie, ROOT, LIMIT).superNodes().get(0).dominantEmotion())
        .isEqualTo("anxiety");

    GraphView none = star(200, i -> null);
    assertThat(GraphGrouping.apply(none, ROOT, LIMIT).superNodes().get(0).dominantEmotion())
        .isNull();
  }

  @Test
  void degreeOneNodesOfDifferentTypesFormDistinctSuperNodes() {
    List<GraphNode> nodes = new ArrayList<>();
    List<GraphEdge> edges = new ArrayList<>();
    nodes.add(node(ROOT, "situation", null, 200, 0, 0));
    for (int i = 0; i < 100; i++) {
      nodes.add(node(id(i), "feeling", "anxiety", 1, 1, i));
      edges.add(edge(ROOT, id(i)));
    }
    for (int i = 100; i < 200; i++) {
      nodes.add(node(id(i), "event", null, 1, 1, i));
      edges.add(edge(id(i), ROOT));
    }

    GraphView out = GraphGrouping.apply(view(nodes, edges), ROOT, LIMIT - 50);

    assertThat(out.superNodes())
        .extracting(GraphSuperNode::id)
        .containsExactly("sn:" + ROOT + ":event", "sn:" + ROOT + ":feeling");
    assertThat(out.superNodes()).extracting(GraphSuperNode::count).containsExactly(100, 100);
    assertThat(out.nodes()).extracting(GraphNode::id).containsExactly(ROOT);
  }

  @Test
  void aGroupOfOneMemberIsNotGrouped() {
    // 150 voisins de degré 2 (non regroupables) + 1 seul ressenti de degré 1
    List<GraphNode> nodes = new ArrayList<>();
    List<GraphEdge> edges = new ArrayList<>();
    nodes.add(node(ROOT, "situation", null, 151, 0, 0));
    for (int i = 0; i < 150; i++) {
      nodes.add(node(id(i), "event", null, 2, 1, i));
      edges.add(edge(ROOT, id(i)));
    }
    UUID lonely = id(999);
    nodes.add(node(lonely, "feeling", "calm", 1, 1, 0));
    edges.add(edge(ROOT, lonely));

    GraphView out = GraphGrouping.apply(view(nodes, edges), ROOT, LIMIT);

    assertThat(out.superNodes()).isEmpty();
    assertThat(out.nodes()).hasSize(LIMIT);
    assertThat(out.truncated()).isTrue();
    assertThat(out.nodes()).extracting(GraphNode::id).contains(ROOT);
  }

  @Test
  void truncationKeepsTheRootAndTheClosestAndDropsOrphanEdges() {
    List<GraphNode> nodes = new ArrayList<>();
    List<GraphEdge> edges = new ArrayList<>();
    nodes.add(node(ROOT, "situation", null, 100, 0, 0));
    // 100 voisins à la distance 1 (degré 2) et 100 à la distance 2 (degré 2)
    for (int i = 0; i < 100; i++) {
      nodes.add(node(id(i), "event", null, 2, 1, i));
      edges.add(edge(ROOT, id(i)));
      nodes.add(node(id(500 + i), "note", null, 2, 2, i));
      edges.add(edge(id(i), id(500 + i)));
    }

    GraphView out = GraphGrouping.apply(view(nodes, edges), ROOT, LIMIT);

    assertThat(out.nodes()).hasSize(LIMIT);
    assertThat(out.truncated()).isTrue();
    assertThat(out.nodes()).extracting(GraphNode::id).contains(ROOT);
    // tous les voisins directs sont gardés avant les nœuds de distance 2
    assertThat(out.nodes().stream().filter(n -> n.distance() == 1)).hasSize(100);
    assertThat(out.nodes().stream().filter(n -> n.distance() == 2)).hasSize(49);
    List<UUID> kept = out.nodes().stream().map(GraphNode::id).toList();
    assertThat(out.edges())
        .allSatisfy(e -> assertThat(kept).contains(e.source()).contains(e.target()));
    assertThat(out.edges()).hasSize(100 + 49);
  }

  @Test
  void theRootIsNeverGroupedEvenWithDegreeOne() {
    List<GraphNode> nodes = new ArrayList<>();
    List<GraphEdge> edges = new ArrayList<>();
    UUID hub = id(7777);
    nodes.add(node(ROOT, "feeling", "calm", 1, 0, 0));
    nodes.add(node(hub, "situation", null, 201, 1, 0));
    edges.add(edge(hub, ROOT));
    for (int i = 0; i < 200; i++) {
      nodes.add(node(id(i), "feeling", "calm", 1, 2, i));
      edges.add(edge(hub, id(i)));
    }

    GraphView out = GraphGrouping.apply(view(nodes, edges), ROOT, LIMIT);

    assertThat(out.nodes()).extracting(GraphNode::id).containsExactlyInAnyOrder(ROOT, hub);
    assertThat(out.superNodes()).hasSize(1);
    assertThat(out.superNodes().get(0).count()).isEqualTo(200);
    assertThat(out.superNodes().get(0).neighborId()).isEqualTo(hub);
    assertThat(out.edges()).hasSize(1);
  }

  @Test
  void aSuperNodeWhoseHubIsTruncatedIsDropped() {
    List<GraphNode> nodes = new ArrayList<>();
    List<GraphEdge> edges = new ArrayList<>();
    nodes.add(node(ROOT, "situation", null, 160, 0, 0));
    // 160 voisins directs de degré 2 ; le dernier sert de hub à 10 ressentis de degré 1
    for (int i = 0; i < 160; i++) {
      nodes.add(node(id(i), "event", null, 2, 1, i));
      edges.add(edge(ROOT, id(i)));
    }
    UUID hub = id(0);
    for (int i = 0; i < 10; i++) {
      nodes.add(node(id(2000 + i), "feeling", "fear", 1, 2, i));
      edges.add(edge(hub, id(2000 + i)));
    }

    GraphView out = GraphGrouping.apply(view(nodes, edges), ROOT, LIMIT);

    assertThat(out.truncated()).isTrue();
    List<UUID> kept = out.nodes().stream().map(GraphNode::id).toList();
    assertThat(kept).hasSize(LIMIT);
    out.superNodes().forEach(sn -> assertThat(kept).contains(sn.neighborId()));
    assertThat(out.edges())
        .allSatisfy(e -> assertThat(kept).contains(e.source()).contains(e.target()));
  }
}
