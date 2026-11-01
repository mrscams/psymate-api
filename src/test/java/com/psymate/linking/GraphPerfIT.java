package com.psymate.linking;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.PostgresTestConfig;
import com.psymate.clinical.application.EntryService;
import com.psymate.clinical.domain.EntryType;
import com.psymate.linking.api.GraphQuery;
import com.psymate.linking.api.GraphQueryFacade;
import com.psymate.linking.api.GraphView;
import com.psymate.linking.persistence.GraphRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Performance du voisinage (M2-03, A4.4) sur un patient seed de 2 000 entrées et 1 500 liens
 * acceptés (quelques hubs et des liens aléatoires à graine fixe). Le seuil de 300 ms médians à
 * profondeur 2 est mesuré et journalisé (« M2-03 perf »), pas imposé : le test n'échoue qu'au-delà
 * de 5 s, pour ne pas dépendre de la machine d'intégration.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class GraphPerfIT {

  private static final Logger log = LoggerFactory.getLogger(GraphPerfIT.class);

  private static final int ENTRIES = 2_000;
  private static final int LINKS = 1_500;
  private static final int HUBS = 5;
  private static final int WARMUPS = 2;
  private static final int RUNS = 5;
  private static final long HARD_LIMIT_MS = 5_000;
  private static final String[] LINK_TYPES = {"triggers", "co_occurs", "follows"};

  @Autowired private GraphQueryFacade graph;
  @Autowired private GraphRepository graphRepository;
  @Autowired private EntryService entries;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  private static final EntryType[] MIX = {
    EntryType.NOTE, EntryType.EVENT, EntryType.FEELING, EntryType.FEELING, EntryType.MEDICATION
  };

  @Test
  void depthTwoNeighbourhoodOfA2000EntryPatientIsFast() {
    LinkingFixtures fx = new LinkingFixtures(jdbc, entries);
    UUID patient = fx.newPatient();
    UUID session = fx.session(patient);
    List<UUID> ids = new ArrayList<>(ENTRIES);
    for (int i = 0; i < ENTRIES; i++) {
      ids.add(fx.entry(patient, session, MIX[i % MIX.length]));
    }

    // Les types existants de la table de référence ; la matrice n'est pas vérifiée par la base.
    List<String> known = jdbc.queryForList("select code from link_type", String.class);
    assertThat(known).contains(LINK_TYPES);

    Random random = new Random(42);
    Set<String> seen = new HashSet<>();
    List<UUID[]> pairs = new ArrayList<>();
    List<UUID> hubs = ids.subList(0, HUBS);
    for (UUID hub : hubs) {
      for (int i = 0; i < 100; i++) {
        addPair(pairs, seen, hub, ids.get(HUBS + random.nextInt(ENTRIES - HUBS)));
      }
    }
    while (pairs.size() < LINKS) {
      addPair(pairs, seen, ids.get(random.nextInt(ENTRIES)), ids.get(random.nextInt(ENTRIES)));
    }
    insertLinks(fx, patient, pairs);

    UUID hub = graphRepository.mostLinkedEntries(patient, 1, null).get(0);
    GraphQuery fromHub = new GraphQuery(patient, hub, 2, null, null, null);
    GraphQuery fromPatient = new GraphQuery(patient, null, 2, null, null, null);
    GraphQuery depthThree = new GraphQuery(patient, hub, 3, null, null, null);

    long hubMs = median(() -> graph.neighborhood(fromHub));
    long patientMs = median(() -> graph.neighborhood(fromPatient));
    long depthThreeMs = median(() -> graph.neighborhood(depthThree));
    long cteMs = median(() -> graphRepository.neighborhood(patient, hub, 2, null));

    GraphView view = graph.neighborhood(fromHub);
    GraphView depth3 = graph.neighborhood(depthThree);
    String summary =
        "M2-03 perf: depth 2 root=hub %d ms, patient root %d ms, depth 3 root=hub %d ms, raw CTE"
                .formatted(hubMs, patientMs, depthThreeMs)
            + " depth 2 %d ms (medians of %d runs after %d warm-ups; %d entries, %d links; depth 2"
                .formatted(cteMs, RUNS, WARMUPS, ENTRIES, LINKS)
            + " graph %d nodes %d edges %d super-nodes truncated=%s; depth 3 graph %d nodes)"
                .formatted(
                    view.nodes().size(),
                    view.edges().size(),
                    view.superNodes().size(),
                    view.truncated(),
                    depth3.nodes().size());
    log.info(summary);
    System.out.println(summary);

    assertThat(hubMs).isLessThan(HARD_LIMIT_MS);
    assertThat(patientMs).isLessThan(HARD_LIMIT_MS);
    assertThat(depthThreeMs).isLessThan(HARD_LIMIT_MS);
    assertThat(view.nodes().size()).isLessThanOrEqualTo(150);
  }

  private static void addPair(List<UUID[]> pairs, Set<String> seen, UUID a, UUID b) {
    if (a.equals(b)) {
      return;
    }
    String type = LINK_TYPES[pairs.size() % LINK_TYPES.length];
    if (seen.add(a + ">" + b + ":" + type)) {
      pairs.add(new UUID[] {a, b});
    }
  }

  private void insertLinks(LinkingFixtures fx, UUID patient, List<UUID[]> pairs) {
    List<Object[]> rows = new ArrayList<>();
    for (int i = 0; i < pairs.size(); i++) {
      UUID[] p = pairs.get(i);
      rows.add(
          new Object[] {
            UUID.randomUUID(),
            fx.tenant,
            patient,
            p[0],
            p[1],
            LINK_TYPES[i % LINK_TYPES.length],
            fx.practitioner
          });
    }
    jdbc.batchUpdate(
        "insert into link (id, tenant_id, patient_id, source_id, target_id, link_type, directed,"
            + " provenance, status, author_id) values (?, ?, ?, ?, ?, ?, true, 'manual',"
            + " 'accepted', ?)",
        rows);
    jdbc.execute("analyze link");
    jdbc.execute("analyze entry");
    jdbc.execute("analyze entry_version");
    jdbc.execute("analyze entry_feeling");
  }

  private static long median(Supplier<?> call) {
    for (int i = 0; i < WARMUPS; i++) {
      call.get();
    }
    long[] ms = new long[RUNS];
    for (int i = 0; i < RUNS; i++) {
      long start = System.nanoTime();
      call.get();
      ms[i] = (System.nanoTime() - start) / 1_000_000;
    }
    Arrays.sort(ms);
    return ms[RUNS / 2];
  }
}
