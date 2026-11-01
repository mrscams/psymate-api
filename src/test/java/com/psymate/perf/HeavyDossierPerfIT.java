package com.psymate.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.psymate.seed.Plan;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.Scenario;
import com.psymate.seed.StepExecutor.Result;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Cibles de latence de A6 sur UN dossier lourd (10 ans, 2 000 entrées, 1 500 liens par défaut),
 * construit par l'API avec le générateur partagé de {@code tools/seed}. Pour chaque requête : 5
 * échauffements puis 40 mesures (temps client MockMvc, sans réseau), p50, p95 et max.
 *
 * <p>La cible A6 n'est PAS bloquante : le test n'échoue qu'au-delà de 5 s (comme GraphPerfIT) ; le
 * rapport {@code target/perf-report.md} dit si chaque cible est tenue, et la session qui lance les
 * tests ouvre une issue sinon. Paramètres : {@code -Dperf.entries=2000 -Dperf.links=1500}.
 */
class HeavyDossierPerfIT extends PerfSupport {

  private static final int WARMUPS = 5;
  private static final int RUNS = 40;
  private static final double HARD_LIMIT_MS = 5_000;

  private record Measure(
      String endpoint,
      String query,
      double p50,
      double p95,
      double max,
      double targetMs,
      String targetLabel) {

    boolean met() {
      return p95 < targetMs;
    }
  }

  private final List<Measure> measures = new ArrayList<>();

  @Test
  void a6TargetsOnAHeavyDossier() throws Exception {
    int entries = Integer.getInteger("perf.entries", 2000);
    int links = Integer.getInteger("perf.links", 1500);
    Plan plan = Scenario.heavy(42, entries, links);
    assertThat(Scenario.validate(plan)).isEmpty();
    PatientPlan dossier = plan.patients().get(0);
    Scenario.Stats stats = Scenario.stats(plan);

    long t0 = System.nanoTime();
    Result written = execute(dossier);
    Duration writing = Duration.ofNanos(System.nanoTime() - t0);
    long t1 = System.nanoTime();
    int pending = drainEventsAndAnalyze();
    Duration draining = Duration.ofNanos(System.nanoTime() - t1);
    String patientId = written.patientId();
    String hubRef = Scenario.mostLinkedSituation(dossier);
    String hubId = written.ids().get(hubRef);
    assertThat(hubId).isNotNull();
    String base = "/api/patients/" + patientId;

    // ---- vue d'ensemble : première ouverture à froid (projection absente, recalcul à la lecture)
    jdbc.update("delete from patient_overview where patient_id = ?", UUID.fromString(patientId));
    double coldOverview = timeOnce(base + "/overview?window=90d");

    // ---- timeline : première page de 200 éléments
    String timelineUrl = base + "/timeline?limit=200&includePrivate=true";
    int firstPage = JsonPath.read(body(perform("GET", timelineUrl, null)), "$.items.length()");
    assertThat(firstPage).isEqualTo(200);
    measure("timeline", "limit=200&includePrivate=true (1re page)", timelineUrl, 150, "< 150 ms");

    // ---- recherche
    measure(
        "recherche",
        "q=" + Scenario.FREQUENT_WORD + " (mot fréquent)",
        base + "/search?q=" + Scenario.FREQUENT_WORD,
        300,
        "< 300 ms (p95)");
    measure(
        "recherche",
        "q=licencimen (faute de frappe, repli trigrammes)",
        base + "/search?q=licencimen",
        300,
        "< 300 ms (p95)");
    measure(
        "recherche",
        "q=ophtalmologue (mot rare)",
        base + "/search?q=ophtalmologue",
        300,
        "< 300 ms (p95)");
    // Une recherche sans résultat serait rapide pour de mauvaises raisons : chaque requête mesurée
    // doit renvoyer des résultats (exacts ou approchés pour la faute de frappe).
    for (String q : List.of(Scenario.FREQUENT_WORD, "licencimen", "ophtalmologue")) {
      String json = body(perform("GET", base + "/search?q=" + q, null));
      long total = ((Number) JsonPath.read(json, "$.total")).longValue();
      long approximate = ((Number) JsonPath.read(json, "$.approximateCount")).longValue();
      assertThat(total + approximate).as("résultats de la recherche « %s »", q).isPositive();
    }

    // ---- vue d'ensemble
    measure("vue d'ensemble", "window=90d", base + "/overview?window=90d", 200, "< 200 ms");

    // ---- graphe
    String graphUrl = base + "/graph?root=" + hubId + "&depth=2";
    int nodes = JsonPath.read(body(perform("GET", graphUrl, null)), "$.nodes.length()");
    assertThat(nodes).isLessThanOrEqualTo(150);
    measure(
        "graphe",
        "root=<situation la plus liée>&depth=2 (" + nodes + " nœuds)",
        graphUrl,
        300,
        "< 300 ms");
    String patientGraphUrl = base + "/graph?depth=2";
    int patientNodes =
        JsonPath.read(body(perform("GET", patientGraphUrl, null)), "$.nodes.length()");
    assertThat(patientNodes).isLessThanOrEqualTo(150);
    measure(
        "graphe",
        "sans root, depth=2 (racine patient, " + patientNodes + " nœuds)",
        patientGraphUrl,
        300,
        "< 300 ms");

    String report = report(stats, written, writing, draining, pending, coldOverview);
    Path target = Path.of("target", "perf-report.md");
    Files.createDirectories(target.getParent());
    Files.writeString(target, report, StandardCharsets.UTF_8);
    System.out.println(report);

    for (Measure m : measures) {
      assertThat(m.p50()).as(m.endpoint() + " " + m.query() + " p50").isLessThan(HARD_LIMIT_MS);
    }
    assertThat(coldOverview)
        .as("première ouverture de la vue d'ensemble")
        .isLessThan(HARD_LIMIT_MS);
  }

  // ---- mesures

  private static String body(MvcResult result) throws IOException {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private double timeOnce(String url) {
    long start = System.nanoTime();
    MvcResult r = perform("GET", url, null);
    double ms = (System.nanoTime() - start) / 1e6;
    assertThat(r.getResponse().getStatus()).as(url).isEqualTo(200);
    return ms;
  }

  private void measure(String endpoint, String query, String url, double targetMs, String label) {
    for (int i = 0; i < WARMUPS; i++) {
      timeOnce(url);
    }
    double[] samples = new double[RUNS];
    for (int i = 0; i < RUNS; i++) {
      samples[i] = timeOnce(url);
    }
    Arrays.sort(samples);
    double p50 = samples[RUNS / 2];
    double p95 = samples[(int) Math.ceil(0.95 * RUNS) - 1];
    double max = samples[RUNS - 1];
    measures.add(new Measure(endpoint, query, p50, p95, max, targetMs, label));
  }

  // ---- rapport

  private String report(
      Scenario.Stats stats,
      Result written,
      Duration writing,
      Duration draining,
      int pending,
      double coldOverview) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Rapport de performance M7-01 (cibles A6)\n\n");
    sb.append("## Jeu de données\n\n");
    sb.append("Un dossier lourd généré par `Scenario.heavy(42, ")
        .append(Integer.getInteger("perf.entries", 2000))
        .append(", ")
        .append(Integer.getInteger("perf.links", 1500))
        .append(")` (10 ans d'historique, praticien de test), écrit par l'API REST (MockMvc) : ")
        .append(stats.describe())
        .append(". Écrit en ")
        .append(pretty(writing))
        .append(" (")
        .append(written.entries())
        .append(" entrées, ")
        .append(written.sessions())
        .append(" séances, ")
        .append(written.links())
        .append(" liens, ")
        .append(written.patches())
        .append(" corrections) ; événements drainés puis ANALYZE en ")
        .append(pretty(draining))
        .append(pending == 0 ? "." : ", dont " + pending + " publication(s) restées incomplètes.")
        .append("\n\n");
    sb.append("## Résultats\n\n");
    sb.append("Mesure côté client MockMvc (sans réseau), ")
        .append(WARMUPS)
        .append(" échauffements puis ")
        .append(RUNS)
        .append(
            " exécutions par requête, en millisecondes. « Tenue ? » compare le p95 à la cible A6.\n\n");
    sb.append("| Endpoint | Requête | p50 | p95 | max | Cible A6 | Tenue ? |\n");
    sb.append("| --- | --- | ---: | ---: | ---: | --- | --- |\n");
    for (Measure m : measures) {
      sb.append(
          String.format(
              Locale.FRENCH,
              "| %s | `%s` | %.1f | %.1f | %.1f | %s | %s |%n",
              m.endpoint(),
              m.query(),
              m.p50(),
              m.p95(),
              m.max(),
              m.targetLabel(),
              m.met() ? "oui" : "NON"));
    }
    sb.append(
        String.format(
            Locale.FRENCH,
            "| vue d'ensemble | `window=90d` (première ouverture à froid, projection absente, une mesure) | %.1f | - | %.1f | < 200 ms | %s |%n",
            coldOverview,
            coldOverview,
            coldOverview < 200 ? "oui" : "NON (une seule mesure)"));
    sb.append("\n## Environnement\n\n");
    sb.append("- Date : ").append(Instant.now()).append('\n');
    sb.append("- JVM : ")
        .append(System.getProperty("java.vm.name"))
        .append(' ')
        .append(System.getProperty("java.runtime.version"))
        .append('\n');
    sb.append("- Système : ")
        .append(System.getProperty("os.name"))
        .append(' ')
        .append(System.getProperty("os.version"))
        .append(' ')
        .append(System.getProperty("os.arch"))
        .append(", ")
        .append(Runtime.getRuntime().availableProcessors())
        .append(" processeurs logiques, ")
        .append(Runtime.getRuntime().maxMemory() / (1024 * 1024))
        .append(" Mo de tas maximum\n");
    sb.append(
        "- Base : PostgreSQL 16 + pgvector par Testcontainers (`pgvector/pgvector:pg16`), profil `test`\n");
    sb.append("- Outil : `./mvnw -B -Pperf verify` (`make perf`), test `HeavyDossierPerfIT`\n");
    return sb.toString();
  }

  private static String pretty(Duration d) {
    long s = d.toSeconds();
    return s >= 60
        ? "%d min %02d s".formatted(s / 60, s % 60)
        : "%.1f s".formatted(d.toMillis() / 1000.0);
  }
}
