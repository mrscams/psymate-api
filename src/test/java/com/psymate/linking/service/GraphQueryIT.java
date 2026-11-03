package com.psymate.linking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.psymate.clinical.model.EntryType;
import com.psymate.clinical.model.TimeSpec;
import com.psymate.clinical.service.CreateEntryCommand;
import com.psymate.clinical.service.EntryService;
import com.psymate.linking.LinkingFixtures;
import com.psymate.linking.model.GraphEdge;
import com.psymate.linking.model.GraphNode;
import com.psymate.linking.model.GraphRequestException;
import com.psymate.linking.model.GraphSuperNode;
import com.psymate.linking.model.GraphView;
import com.psymate.linking.model.LinkError;
import com.psymate.linking.model.LinkException;
import com.psymate.patients.api.PatientAccessException;
import com.psymate.testsupport.SharedPostgres;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Requête de voisinage et niveau de détail (M2-03, A4.4, A6) par la façade, sur PostgreSQL réel.
 *
 * <p>Graphe connu : chaîne A-B-C-D-E (A triggers B, B co_occurs C, C triggers D, D co_occurs E) et
 * triangle E-X-Y (avec un part_of réciproque X/Y posé en SQL) pour la garde de cycle.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class GraphQueryIT {

  private static final Instant D1 = Instant.parse("2025-01-01T10:00:00Z");

  @Autowired private GraphQueryService graph;
  @Autowired private LinkService links;
  @Autowired private EntryService entries;
  @Autowired private JdbcTemplate jdbc;

  private LinkingFixtures fx;
  private UUID patient;
  private UUID session;

  private UUID a;
  private UUID b;
  private UUID c;
  private UUID d;
  private UUID e;
  private UUID x;
  private UUID y;
  private UUID loner;

  @BeforeEach
  void setUp() {
    fx = new LinkingFixtures(jdbc, entries);
    patient = fx.newPatient();
    session = fx.session(patient);
    a = event("A", 1);
    b = event("B", 2);
    c = event("C", 3);
    d = event("D", 4);
    e = event("E", 5);
    x = event("X", 6);
    y = event("Y", 7);
    loner = event("Seule", 8);
    links.create(a, b, "triggers", null);
    links.create(b, c, "co_occurs", null);
    links.create(c, d, "triggers", null);
    links.create(d, e, "co_occurs", null);
    links.create(e, x, "co_occurs", null);
    links.create(x, y, "follows", null);
    links.create(y, e, "follows", null);
    // part_of réciproque : deux liens de sens opposés entre les mêmes entrées
    insertLink(x, y, "part_of");
    insertLink(y, x, "part_of");
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- fabrique

  private UUID event(String title, int day) {
    return entries.create(
        patient,
        new CreateEntryCommand(
            EntryType.EVENT,
            session,
            title,
            null,
            null,
            null,
            TimeSpec.exact(D1.plus(Duration.ofDays(day))),
            null,
            null,
            null));
  }

  private void insertLink(UUID source, UUID target, String type) {
    insertLinks(patient, Collections.singletonList(new UUID[] {source, target}), type);
  }

  /** Lien accepté posé directement dans link (table du module), pour les gros volumes. */
  private void insertLinks(UUID forPatient, List<UUID[]> pairs, String type) {
    jdbc.batchUpdate(
        "insert into link (id, tenant_id, patient_id, source_id, target_id, link_type, directed,"
            + " provenance, status, author_id) values (?, ?, ?, ?, ?, ?, true, 'manual',"
            + " 'accepted', ?)",
        pairs,
        500,
        (ps, pair) -> {
          ps.setObject(1, UUID.randomUUID());
          ps.setObject(2, fx.tenant);
          ps.setObject(3, forPatient);
          ps.setObject(4, pair[0]);
          ps.setObject(5, pair[1]);
          ps.setString(6, type);
          ps.setObject(7, fx.practitioner);
        });
  }

  private GraphView hood(UUID root, int depth) {
    return graph.neighborhood(new GraphQuery(patient, root, depth, null, null, null));
  }

  private static Set<String> titles(GraphView view) {
    return view.nodes().stream().map(GraphNode::title).collect(Collectors.toSet());
  }

  private static Map<String, Integer> distances(GraphView view) {
    return view.nodes().stream().collect(Collectors.toMap(GraphNode::title, GraphNode::distance));
  }

  private Set<String> edgeNames(GraphView view, Map<UUID, String> names) {
    return view.edges().stream()
        .map(
            ed -> {
              String s = names.get(ed.source());
              String t = names.get(ed.target());
              return s.compareTo(t) < 0 ? s + t : t + s;
            })
        .collect(Collectors.toSet());
  }

  private Map<UUID, String> names() {
    return Map.of(a, "A", b, "B", c, "C", d, "D", e, "E", x, "X", y, "Y");
  }

  private static <T extends Throwable> T thrown(Class<T> type, ThrowingCallable action) {
    Throwable t = catchThrowable(action);
    assertThat(t).isInstanceOf(type);
    return type.cast(t);
  }

  // ---- profondeur et distances

  @Test
  void depthOneFromAGivesAAndB() {
    GraphView view = hood(a, 1);

    assertThat(titles(view)).containsExactlyInAnyOrder("A", "B");
    assertThat(distances(view)).containsEntry("A", 0).containsEntry("B", 1);
    assertThat(view.edges()).hasSize(1);
    assertThat(view.superNodes()).isEmpty();
    assertThat(view.truncated()).isFalse();
  }

  @Test
  void depthTwoFromAGivesABAndC() {
    GraphView view = hood(a, 2);

    assertThat(titles(view)).containsExactlyInAnyOrder("A", "B", "C");
    assertThat(distances(view)).containsEntry("C", 2);
    assertThat(edgeNames(view, names())).containsExactlyInAnyOrder("AB", "BC");
  }

  @Test
  void depthThreeFromAIncludesDAndExcludesE() {
    GraphView view = hood(a, 3);

    assertThat(titles(view)).containsExactlyInAnyOrder("A", "B", "C", "D");
    assertThat(distances(view))
        .containsEntry("A", 0)
        .containsEntry("B", 1)
        .containsEntry("C", 2)
        .containsEntry("D", 3);
    assertThat(edgeNames(view, names())).containsExactlyInAnyOrder("AB", "BC", "CD");
  }

  @Test
  void cyclesDoNotLoopAndDistancesAreMinimal() {
    // depuis E : D, X, Y à 1 (le triangle E-X-Y, et les part_of réciproques), C à 2, B à 3
    GraphView view = hood(e, 3);

    assertThat(distances(view))
        .containsEntry("E", 0)
        .containsEntry("D", 1)
        .containsEntry("X", 1)
        .containsEntry("Y", 1)
        .containsEntry("C", 2)
        .containsEntry("B", 3)
        .hasSize(6);
    // deux liens X-Y réciproques (part_of) et le follows
    assertThat(view.edges().stream().filter(ed -> ed.linkType().equals("part_of"))).hasSize(2);

    GraphView fromX = hood(x, 3);
    assertThat(distances(fromX)).containsEntry("X", 0).containsEntry("Y", 1).containsEntry("E", 1);
  }

  @Test
  void degreeIsGlobalNotLimitedToTheNeighbourhood() {
    GraphView view = hood(a, 1);

    Map<String, Integer> degrees =
        view.nodes().stream().collect(Collectors.toMap(GraphNode::title, GraphNode::degree));
    assertThat(degrees).containsEntry("A", 1).containsEntry("B", 2);
    GraphView wide = hood(e, 1);
    assertThat(wide.nodes().stream().filter(n -> n.title().equals("E")).findFirst().get().degree())
        .isEqualTo(3);
  }

  @Test
  void nodesCarryTheEntryAttributes() {
    UUID feeling = fx.entry(patient, session, EntryType.FEELING);
    links.create(a, feeling, "triggers", null);

    GraphView view = hood(a, 1);

    GraphNode f =
        view.nodes().stream().filter(n -> n.id().equals(feeling)).findFirst().orElseThrow();
    assertThat(f.type()).isEqualTo("feeling");
    assertThat(f.emotion()).isEqualTo("anxiety");
    assertThat(f.intensity()).isEqualTo(7);
    assertThat(f.authorType()).isEqualTo("practitioner");
    assertThat(f.occurred()).isNotNull();
    GraphNode root = view.nodes().stream().filter(n -> n.id().equals(a)).findFirst().orElseThrow();
    assertThat(root.type()).isEqualTo("event");
    assertThat(root.occurred()).isEqualTo(D1.plus(Duration.ofDays(1)));
    assertThat(root.emotion()).isNull();
    assertThat(root.intensity()).isNull();
    GraphEdge edge = view.edges().get(0);
    assertThat(edge.provenance()).isEqualTo("manual");
    assertThat(edge.status()).isEqualTo("accepted");
    assertThat(edge.directed()).isTrue();
  }

  // ---- filtres

  @Test
  void linkTypesFilterBothTraversalAndEdges() {
    GraphView triggersOnly =
        graph.neighborhood(new GraphQuery(patient, a, 3, List.of("triggers"), null, null));
    // B co_occurs C n'est pas suivi : C et D sont hors de portée
    assertThat(titles(triggersOnly)).containsExactlyInAnyOrder("A", "B");

    GraphView both =
        graph.neighborhood(
            new GraphQuery(patient, a, 3, List.of("triggers", "co_occurs"), null, null));
    assertThat(titles(both)).containsExactlyInAnyOrder("A", "B", "C", "D");
    assertThat(both.edges()).extracting(GraphEdge::linkType).containsOnly("triggers", "co_occurs");

    GraphView coOccursFromC =
        graph.neighborhood(new GraphQuery(patient, c, 3, List.of("co_occurs"), null, null));
    assertThat(titles(coOccursFromC)).containsExactlyInAnyOrder("B", "C");
    assertThat(coOccursFromC.edges()).hasSize(1);
  }

  @Test
  void unknownLinkTypeIsRefused() {
    GraphRequestException ex =
        thrown(
            GraphRequestException.class,
            () -> graph.neighborhood(new GraphQuery(patient, a, 2, List.of("nope"), null, null)));
    assertThat(ex.problems())
        .containsExactly(new GraphRequestException.Problem("linkTypes", "INVALID_VALUE"));
  }

  @Test
  void periodRemovesNodesOutsideExceptTheRoot() {
    // A=J+1 (racine), B=J+2, C=J+3, D=J+4
    GraphView view =
        graph.neighborhood(
            new GraphQuery(
                patient,
                a,
                3,
                null,
                D1.plus(Duration.ofDays(3)).minusSeconds(60),
                D1.plus(Duration.ofDays(4)).plusSeconds(60)));

    assertThat(titles(view)).containsExactlyInAnyOrder("A", "C", "D");
    assertThat(distances(view)).containsEntry("A", 0).containsEntry("C", 2).containsEntry("D", 3);
    assertThat(edgeNames(view, names())).containsExactly("CD");
  }

  @Test
  void periodBoundsAreFromInclusiveAndToExclusiveLikeTheTimeline() {
    // C=J+3 exactement : inclus par from = J+3, exclu par to = J+3
    GraphView fromAtC =
        graph.neighborhood(new GraphQuery(patient, a, 3, null, D1.plus(Duration.ofDays(3)), null));
    assertThat(titles(fromAtC)).contains("C");

    GraphView toAtC =
        graph.neighborhood(new GraphQuery(patient, a, 3, null, null, D1.plus(Duration.ofDays(3))));
    assertThat(titles(toAtC)).contains("B").doesNotContain("C", "D");
  }

  @Test
  void invertedPeriodIsRefused() {
    GraphRequestException ex =
        thrown(
            GraphRequestException.class,
            () -> graph.neighborhood(new GraphQuery(patient, a, 2, null, D1.plusSeconds(10), D1)));
    assertThat(ex.problems())
        .containsExactly(new GraphRequestException.Problem("from", "INVALID_TIME"));
  }

  // ---- archivage

  @Test
  void anArchivedEntryIsExcludedWithItsEdges() {
    // L'abonné d'EntryArchived passe derrière l'arriéré du registre : on l'écoule d'abord, pour
    // que le délai ci-dessous ne mesure que lui.
    fx.awaitPublicationsSettled(patient);
    entries.archive(c);

    GraphView immediate = hood(a, 3);
    assertThat(titles(immediate)).doesNotContain("C");
    assertThat(immediate.edges()).noneMatch(ed -> ed.source().equals(c) || ed.target().equals(c));

    // le lien passe superseded à l'écoute de EntryArchived : D n'est alors plus atteignable
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        fx.count(
                            "select count(*) from link where status = 'accepted'"
                                + " and (source_id = ? or target_id = ?)",
                            c,
                            c))
                    .isZero());
    GraphView settled = hood(a, 3);
    assertThat(titles(settled)).containsExactlyInAnyOrder("A", "B");
    assertThat(settled.edges()).hasSize(1);
  }

  @Test
  void anArchivedRootIsRefused() {
    entries.archive(a);

    LinkException ex = thrown(LinkException.class, () -> hood(a, 2));
    assertThat(ex.error()).isEqualTo(LinkError.ENTRY_ARCHIVED);
    assertThat(ex.field()).isEqualTo("root");
  }

  // ---- racine patient

  @Test
  void patientRootGivesTheMostLinkedEntriesAndTheEdgesBetweenThem() {
    GraphView view = hood(null, 2);

    assertThat(titles(view)).containsExactlyInAnyOrder("A", "B", "C", "D", "E", "X", "Y");
    assertThat(view.nodes()).allSatisfy(n -> assertThat(n.distance()).isEqualTo(1));
    assertThat(view.edges()).hasSize(9);
    assertThat(view.truncated()).isFalse();
    assertThat(view.superNodes()).isEmpty();
    // les plus liés d'abord : X et Y (4 liens chacun)
    assertThat(view.nodes().get(0).title()).isIn("X", "Y");
    assertThat(view.nodes().get(0).degree()).isEqualTo(4);
  }

  @Test
  void patientRootCountsOnlyTheRequestedLinkTypes() {
    // triggers : A-B et C-D seulement ; E, X, Y ne sont reliés que par co_occurs / follows /
    // part_of
    GraphView view =
        graph.neighborhood(new GraphQuery(patient, null, 2, List.of("triggers"), null, null));

    assertThat(titles(view)).containsExactlyInAnyOrder("A", "B", "C", "D");
    assertThat(edgeNames(view, names())).containsExactlyInAnyOrder("AB", "CD");
    assertThat(view.edges()).allSatisfy(ed -> assertThat(ed.linkType()).isEqualTo("triggers"));
  }

  @Test
  void patientRootIsLimitedToTheMostLinkedAndSaysSo() {
    List<UUID[]> pairs = new ArrayList<>();
    UUID hub = event("Hub", 9);
    for (int i = 0; i < GraphQueryService.MAX_NODES + 10; i++) {
      pairs.add(new UUID[] {hub, event("N" + i, 10)});
    }
    insertLinks(patient, pairs, "follows");

    GraphView view = hood(null, 2);

    assertThat(view.nodes()).hasSize(GraphQueryService.MAX_NODES);
    assertThat(view.truncated()).isTrue();
    assertThat(view.nodes().get(0).id()).isEqualTo(hub);
  }

  @Test
  void aPatientWithoutLinksGivesAnEmptyGraph() {
    UUID other = fx.newPatient();
    GraphView view = graph.neighborhood(new GraphQuery(other, null, 2, null, null, null));

    assertThat(view.nodes()).isEmpty();
    assertThat(view.edges()).isEmpty();
    assertThat(view.truncated()).isFalse();
  }

  // ---- gardes

  @Test
  void depthOutsideOneToThreeIsRefused() {
    for (int depth : new int[] {0, 4, -1}) {
      GraphRequestException ex = thrown(GraphRequestException.class, () -> hood(a, depth));
      assertThat(ex.problems())
          .containsExactly(new GraphRequestException.Problem("depth", "OUT_OF_RANGE"));
    }
  }

  @Test
  void unknownRootIsNotFound() {
    LinkException ex = thrown(LinkException.class, () -> hood(UUID.randomUUID(), 2));
    assertThat(ex.error()).isEqualTo(LinkError.ENTRY_NOT_FOUND);
  }

  @Test
  void rootOfAnotherPatientOfTheSamePractitionerIsRefused() {
    UUID otherPatient = fx.newPatient();
    UUID otherSession = fx.session(otherPatient);
    UUID foreign = fx.entry(otherPatient, otherSession, EntryType.NOTE);

    LinkException ex = thrown(LinkException.class, () -> hood(foreign, 2));
    assertThat(ex.error()).isEqualTo(LinkError.DIFFERENT_PATIENT);
    assertThat(ex.field()).isEqualTo("root");
  }

  @Test
  void rootOfAnotherPractitionersPatientIsNotFound() {
    UUID me = fx.practitioner;
    fx.authenticate(fx.newPractitioner());
    UUID theirPatient = fx.newPatient();
    UUID theirEntry = fx.entry(theirPatient, fx.session(theirPatient), EntryType.NOTE);
    fx.authenticate(me);

    LinkException ex = thrown(LinkException.class, () -> hood(theirEntry, 2));
    assertThat(ex.error()).isEqualTo(LinkError.ENTRY_NOT_FOUND);
  }

  @Test
  void aPatientOfAnotherPractitionerIsForbidden() {
    UUID me = fx.practitioner;
    fx.authenticate(fx.newPractitioner());
    UUID theirPatient = fx.newPatient();
    fx.authenticate(me);

    PatientAccessException ex =
        thrown(
            PatientAccessException.class,
            () -> graph.neighborhood(new GraphQuery(theirPatient, null, 2, null, null, null)));
    assertThat(ex.status().value()).isEqualTo(403);
    PatientAccessException expand =
        thrown(
            PatientAccessException.class,
            () -> graph.expand(theirPatient, "sn:" + UUID.randomUUID() + ":feeling", null));
    assertThat(expand.status().value()).isEqualTo(403);
  }

  @Test
  void anUnknownPatientIsNotFound() {
    PatientAccessException ex =
        thrown(
            PatientAccessException.class,
            () -> graph.neighborhood(new GraphQuery(UUID.randomUUID(), null, 2, null, null, null)));
    assertThat(ex.status().value()).isEqualTo(404);
  }

  // ---- niveau de détail et expand

  /** Une situation et 200 ressentis qui lui sont liés ; renvoie la situation. */
  private UUID situationWithFeelings(int count, List<UUID> feelings) {
    UUID situation =
        entries.create(
            patient,
            new CreateEntryCommand(
                EntryType.SITUATION,
                session,
                "Réunion d'équipe",
                null,
                null,
                null,
                TimeSpec.ongoing(
                    com.psymate.clinical.model.TimeSpan.Precision.EXACT, D1.plusSeconds(60)),
                null,
                null,
                null));
    List<UUID[]> pairs = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      UUID f = fx.entry(patient, session, EntryType.FEELING);
      feelings.add(f);
      pairs.add(new UUID[] {situation, f});
    }
    insertLinks(patient, pairs, "triggers");
    return situation;
  }

  @Test
  void twoHundredFeelingsOnOneSituationBecomeOneSuperNode() {
    List<UUID> feelings = new ArrayList<>();
    UUID situation = situationWithFeelings(200, feelings);

    GraphView view = hood(situation, 2);

    assertThat(view.nodes().size()).isLessThanOrEqualTo(GraphQueryService.MAX_NODES);
    assertThat(view.nodes()).extracting(GraphNode::id).containsExactly(situation);
    assertThat(view.truncated()).isTrue();
    assertThat(view.edges()).isEmpty();
    assertThat(view.superNodes()).hasSize(1);
    GraphSuperNode sn = view.superNodes().get(0);
    assertThat(sn.id()).isEqualTo("sn:" + situation + ":feeling");
    assertThat(sn.count()).isEqualTo(200);
    assertThat(sn.dominantEmotion()).isEqualTo("anxiety");
    assertThat(sn.neighborId()).isEqualTo(situation);
    assertThat(sn.memberIds()).containsExactlyInAnyOrderElementsOf(feelings).isSorted();
  }

  @Test
  void expandBySuperNodeIdReturnsTheGroupNodesAndEdges() {
    List<UUID> feelings = new ArrayList<>();
    UUID situation = situationWithFeelings(200, feelings);

    GraphView expanded = graph.expand(patient, "sn:" + situation + ":feeling", null);

    assertThat(expanded.nodes()).hasSize(200);
    assertThat(expanded.nodes())
        .extracting(GraphNode::id)
        .containsExactlyInAnyOrderElementsOf(feelings);
    assertThat(expanded.nodes())
        .allSatisfy(
            n -> {
              assertThat(n.distance()).isEqualTo(1);
              assertThat(n.degree()).isEqualTo(1);
              assertThat(n.type()).isEqualTo("feeling");
            });
    assertThat(expanded.edges()).hasSize(200);
    assertThat(expanded.superNodes()).isEmpty();
    assertThat(expanded.truncated()).isFalse();
  }

  @Test
  void expandByMemberIdsReturnsTheSameGroup() {
    List<UUID> feelings = new ArrayList<>();
    UUID situation = situationWithFeelings(200, feelings);

    GraphView expanded = graph.expand(patient, null, feelings);

    assertThat(expanded.nodes()).hasSize(200);
    assertThat(expanded.edges()).hasSize(200);
    assertThat(expanded.edges()).allSatisfy(ed -> assertThat(ed.source()).isEqualTo(situation));
  }

  @Test
  void aFeelingWithASecondLinkLeavesTheSuperNodeGroup() {
    List<UUID> feelings = new ArrayList<>();
    UUID situation = situationWithFeelings(20, feelings);
    UUID other = event("Autre", 12);
    links.create(other, feelings.get(0), "triggers", null);

    GraphView expanded = graph.expand(patient, "sn:" + situation + ":feeling", null);

    assertThat(expanded.nodes()).hasSize(19);
    assertThat(expanded.nodes()).extracting(GraphNode::id).doesNotContain(feelings.get(0));
  }

  @Test
  void expandKeepsOnlyEdgesBetweenLiveEntries() {
    UUID hub = event("Hub", 9);
    UUID m1 = fx.entry(patient, session, EntryType.FEELING);
    UUID m2 = fx.entry(patient, session, EntryType.FEELING);
    UUID archivedOther = event("Archivée", 10);
    insertLinks(patient, List.of(new UUID[] {hub, m1}, new UUID[] {hub, m2}), "triggers");
    // m1 a un second lien vers une entrée archivée ensuite : memberIds l'accepte quand même
    insertLink(archivedOther, m1, "triggers");
    entries.archive(archivedOther);

    GraphView expanded = graph.expand(patient, null, List.of(m1, m2));

    assertThat(expanded.nodes()).hasSize(2);
    assertThat(expanded.edges())
        .noneMatch(ed -> ed.source().equals(archivedOther) || ed.target().equals(archivedOther));
    assertThat(expanded.edges()).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void unknownOrMalformedSuperNodeIsRefused() {
    List<UUID> feelings = new ArrayList<>();
    UUID situation = situationWithFeelings(3, feelings);

    LinkException unknown =
        thrown(
            LinkException.class,
            () -> graph.expand(patient, "sn:" + UUID.randomUUID() + ":feeling", null));
    assertThat(unknown.error()).isEqualTo(LinkError.SUPER_NODE_NOT_FOUND);

    LinkException wrongType =
        thrown(
            LinkException.class,
            () -> graph.expand(patient, "sn:" + situation + ":medication", null));
    assertThat(wrongType.error()).isEqualTo(LinkError.SUPER_NODE_NOT_FOUND);

    for (String malformed : new String[] {"garbage", "sn:not-a-uuid:feeling", "sn:" + situation}) {
      GraphRequestException ex =
          thrown(GraphRequestException.class, () -> graph.expand(patient, malformed, null));
      assertThat(ex.problems())
          .containsExactly(new GraphRequestException.Problem("superNodeId", "INVALID_FORMAT"));
    }
  }

  @Test
  void aSuperNodeOfAnotherPatientIsNotFound() {
    List<UUID> feelings = new ArrayList<>();
    UUID situation = situationWithFeelings(3, feelings);
    UUID otherPatient = fx.newPatient();

    LinkException ex =
        thrown(
            LinkException.class,
            () -> graph.expand(otherPatient, "sn:" + situation + ":feeling", null));
    assertThat(ex.error()).isEqualTo(LinkError.SUPER_NODE_NOT_FOUND);
  }

  @Test
  void expandNeedsExactlyOneOfSuperNodeIdAndMemberIds() {
    for (Object[] args :
        new Object[][] {
          {"sn:" + UUID.randomUUID() + ":feeling", List.of(UUID.randomUUID())}, {null, null}
        }) {
      GraphRequestException ex =
          thrown(
              GraphRequestException.class,
              () -> graph.expand(patient, (String) args[0], castIds(args[1])));
      assertThat(ex.problems())
          .containsExactly(new GraphRequestException.Problem("superNodeId", "INVALID_VALUE"));
    }
  }

  @SuppressWarnings("unchecked")
  private static List<UUID> castIds(Object o) {
    return (List<UUID>) o;
  }

  @Test
  void memberIdsAreValidated() {
    GraphRequestException empty =
        thrown(GraphRequestException.class, () -> graph.expand(patient, null, List.of()));
    assertThat(empty.problems().get(0).field()).isEqualTo("memberIds");

    List<UUID> tooMany = new ArrayList<>();
    for (int i = 0; i < GraphQueryService.MAX_MEMBER_IDS + 1; i++) {
      tooMany.add(UUID.randomUUID());
    }
    GraphRequestException many =
        thrown(GraphRequestException.class, () -> graph.expand(patient, null, tooMany));
    assertThat(many.problems())
        .containsExactly(new GraphRequestException.Problem("memberIds", "OUT_OF_RANGE"));

    GraphRequestException duplicate =
        thrown(GraphRequestException.class, () -> graph.expand(patient, null, List.of(a, a)));
    assertThat(duplicate.problems())
        .containsExactly(new GraphRequestException.Problem("memberIds", "INVALID_VALUE"));
  }

  @Test
  void memberIdsOfAnotherPatientOrUnknownOrArchivedAreRefused() {
    UUID otherPatient = fx.newPatient();
    UUID foreign = fx.entry(otherPatient, fx.session(otherPatient), EntryType.NOTE);
    UUID archived = event("Archivée", 11);
    entries.archive(archived);

    for (UUID bad : new UUID[] {foreign, UUID.randomUUID(), archived}) {
      GraphRequestException ex =
          thrown(GraphRequestException.class, () -> graph.expand(patient, null, List.of(a, bad)));
      assertThat(ex.problems())
          .containsExactly(new GraphRequestException.Problem("memberIds", "ENTRY_NOT_FOUND"));
    }
  }
}
