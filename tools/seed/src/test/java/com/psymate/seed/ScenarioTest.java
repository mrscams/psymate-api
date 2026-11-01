package com.psymate.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.psymate.seed.Plan.CreateEntry;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.Plan.StartSession;
import com.psymate.seed.Plan.Step;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Le générateur est pur : ces tests vérifient sans réseau les critères de C2 M7-01 qui dépendent du
 * plan (patient DEMO, règles E, ratios de C3) et les garde-fous de {@link Seed}.
 */
class ScenarioTest {

  private static final Instant TODAY = Instant.parse("2026-11-01T00:00:00Z");

  @Test
  void demoPatientHasTwentySessionsOverEighteenMonths() {
    Plan plan = Scenario.demo(TODAY);
    assertEquals(1, plan.patients().size());
    PatientPlan demo = plan.patients().get(0);
    assertTrue(demo.displayName().startsWith("DEMO · "), demo.displayName());
    List<StartSession> sessions =
        demo.steps().stream()
            .filter(StartSession.class::isInstance)
            .map(StartSession.class::cast)
            .toList();
    assertEquals(20, sessions.size());
    Instant first = sessions.get(0).occurredFrom();
    Instant last = sessions.get(sessions.size() - 1).occurredFrom();
    Duration span = Duration.between(first, TODAY);
    assertTrue(span.toDays() >= 17 * 30 && span.toDays() <= 19 * 30, "18 mois attendus : " + span);
    assertTrue(last.isBefore(TODAY), "toutes les séances sont passées");
    assertEquals(List.of(), Scenario.validate(plan));
  }

  @Test
  void smallPlanRespectsRulesAndRatios() {
    Plan plan = Scenario.small(Scenario.DEFAULT_SEED, TODAY);
    assertEquals(5, plan.patients().size());
    assertEquals(List.of(), Scenario.validate(plan));
    Set<String> names = new HashSet<>();
    for (PatientPlan p : plan.patients()) {
      assertTrue(p.displayName().startsWith("SEED · "), p.displayName());
      assertTrue(names.add(p.displayName()), "nom d'affichage en double : " + p.displayName());
      for (Step step : p.steps()) {
        if (step instanceof CreateEntry e) {
          assertTrue(e.sessionRef() != null && !e.sessionRef().isBlank(), "séance obligatoire");
          Map<String, Object> body = e.body();
          assertTrue(!body.containsKey("authorType") && !body.containsKey("submissionId"));
        }
      }
    }
    Scenario.Stats stats = Scenario.stats(plan);
    double imprecise = (double) stats.imprecise() / stats.entries();
    double privateRatio = (double) stats.privateEntries() / stats.entries();
    assertTrue(imprecise >= 0.09 && imprecise <= 0.14, "10 % de dates imprécises : " + imprecise);
    assertTrue(
        privateRatio >= 0.04 && privateRatio <= 0.07, "5 % d'entrées privées : " + privateRatio);
    assertTrue(stats.entries() >= 5 * 150, "environ 200 entrées par patient : " + stats.entries());
  }

  @Test
  void heavyPlanReachesTargetSizes() {
    Plan plan = Scenario.heavy(42, 2000, 1500, TODAY);
    Scenario.Stats stats = Scenario.stats(plan);
    assertEquals(List.of(), Scenario.validate(plan));
    assertEquals(2000, stats.entries());
    assertTrue(stats.links() >= 1500, "au moins 1 500 liens : " + stats.links());
    assertTrue(stats.maxDegree() >= 80, "une situation pivot : " + stats.maxDegree());
    double imprecise = (double) stats.imprecise() / stats.entries();
    assertTrue(imprecise >= 0.08 && imprecise <= 0.14, "10 % de dates imprécises : " + imprecise);
  }

  @Test
  void seedRefusesAnythingButLocalhostBeforeWriting() {
    PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
    RuntimeException remote =
        assertThrows(
            RuntimeException.class,
            () ->
                Seed.run(
                    new String[] {"--size", "small", "--api", "http://example.com:8080"}, out));
    assertTrue(remote.getMessage().contains("n'est pas local"), remote.getMessage());
    RuntimeException noSize =
        assertThrows(RuntimeException.class, () -> Seed.run(new String[] {}, out));
    assertTrue(noSize.getMessage().contains("--size"), noSize.getMessage());
  }

  @Test
  void jsonRoundTrip() {
    String text = "{\"a\":[1,2.5,true,null,\"é\\n\\\"x\\\"\"],\"b\":{\"c\":-3}}";
    Object parsed = Json.parse(text);
    assertEquals(text, Json.write(parsed));
    assertEquals(-3L, ((Map<?, ?>) ((Map<?, ?>) parsed).get("b")).get("c"));
  }
}
