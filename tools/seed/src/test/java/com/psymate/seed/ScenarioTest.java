package com.psymate.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.psymate.seed.Plan.CreateEntry;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.Plan.ProfileItem;
import com.psymate.seed.Plan.StartSession;
import com.psymate.seed.Plan.Step;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

  // ---- Profil 360 (M13-03)

  @Test
  void demoPatientHasAHandwrittenProfileInTheFourCategories() {
    PatientPlan demo = Scenario.demo(TODAY).patients().get(0);
    Map<String, Long> byCategory =
        demo.profile().stream()
            .collect(Collectors.groupingBy(ProfileItem::category, Collectors.counting()));
    long history = byCategory.getOrDefault("history", 0L);
    long family = byCategory.getOrDefault("family", 0L);
    assertTrue(history >= 9 && history <= 12, "une dizaine d'informations d'histoire : " + history);
    assertTrue(family >= 5 && family <= 6, "cinq ou six proches : " + family);
    assertEquals(4L, byCategory.get("health"));
    assertEquals(4L, byCategory.get("values_goals"));
    assertTrue(
        demo.profile().stream()
            .anyMatch(i -> "history".equals(i.category()) && i.periodYear() == null),
        "au moins une information d'histoire sans année");
    assertTrue(
        demo.profile().stream().anyMatch(i -> i.periodLabel() != null && i.periodYear() == null),
        "un libellé sans année (« Enfance »)");
    for (ProfileItem i : demo.profile()) {
      if (i.periodYear() != null) {
        assertTrue(i.periodYear() >= 1989 && i.periodYear() <= 2026, "année plausible : " + i);
      }
    }
    assertEquals(List.of(), Scenario.validate(new Plan(List.of(demo))));
  }

  @Test
  void ordinaryProfilesMixCategoriesPeriodsAndEmptyTabs() {
    Plan plan = Scenario.medium(Scenario.DEFAULT_SEED, TODAY);
    assertEquals(List.of(), Scenario.validate(plan));
    Set<String> categories = new HashSet<>();
    int empty = 0;
    int withYear = 0;
    int labelOnly = 0;
    int noPeriod = 0;
    for (PatientPlan p : plan.patients()) {
      int size = p.profile().size();
      assertTrue(size <= 25, p.displayName() + " : " + size + " informations, 25 au plus");
      if (size == 0) {
        empty++;
      }
      int birthYear = Integer.parseInt(p.birthDate().substring(0, 4));
      Set<String> titles = new HashSet<>();
      for (ProfileItem i : p.profile()) {
        categories.add(i.category());
        assertTrue(titles.add(i.title()), p.displayName() + " : information en double " + i);
        if (!"history".equals(i.category())) {
          assertTrue(i.periodLabel() == null && i.periodYear() == null, "période hors history");
        } else if (i.periodYear() != null) {
          withYear++;
          assertTrue(i.periodLabel() != null, "une année s'affiche avec un libellé : " + i);
          assertTrue(
              i.periodYear() >= birthYear && i.periodYear() <= 2026,
              p.displayName() + " né en " + birthYear + " : " + i);
        } else if (i.periodLabel() != null) {
          labelOnly++;
        } else {
          noPeriod++;
        }
      }
    }
    double emptyRatio = (double) empty / plan.patients().size();
    assertTrue(
        emptyRatio >= 0.1 && emptyRatio <= 0.3, "un patient sur cinq sans profil : " + emptyRatio);
    assertEquals(Set.copyOf(Scenario.PROFILE_CATEGORIES), categories);
    assertTrue(
        withYear > 0 && labelOnly > 0 && noPeriod > 0, withYear + "/" + labelOnly + "/" + noPeriod);
    Scenario.Stats stats = Scenario.stats(plan);
    assertEquals(empty, stats.emptyProfiles());
    assertTrue(stats.profileItems() > 0);
    // même graine, même profil
    assertEquals(plan, Scenario.medium(Scenario.DEFAULT_SEED, TODAY));
  }

  @Test
  void smallPlanHasProfiles() {
    Plan plan = Scenario.small(Scenario.DEFAULT_SEED, TODAY);
    assertTrue(
        plan.patients().stream().anyMatch(p -> !p.profile().isEmpty()),
        "au moins un patient du seed small a un profil à lire");
  }

  @Test
  void validateRejectsProfilesTheApiWouldRefuse() {
    PatientPlan base = Scenario.small(Scenario.DEFAULT_SEED, TODAY).patients().get(0);
    List<ProfileItem> bad =
        List.of(
            new ProfileItem("family", "Sœur", null, "Enfance", null),
            new ProfileItem("health", "Asthme", null, null, 2001),
            new ProfileItem("history", " ", null, null, null),
            new ProfileItem("history", "x".repeat(201), null, null, null),
            new ProfileItem("history", "Souvenir", "d".repeat(2001), "p".repeat(61), 1899),
            new ProfileItem("resources", "Inconnue", null, null, null));
    List<String> problems = Scenario.validate(new Plan(List.of(base.withProfile(bad))));
    assertEquals(8, problems.size(), String.join(" | ", problems));
    List<ProfileItem> tooMany =
        Collections.nCopies(501, new ProfileItem("family", "Ami", null, null, null));
    assertEquals(
        1, Scenario.validate(new Plan(List.of(base.withProfile(tooMany)))).size(), "plafond 500");
  }

  @Test
  void profileBodyOmitsNullFields() {
    assertEquals(
        Map.of("category", "family", "title", "Sœur"),
        new ProfileItem("family", "Sœur", null, null, null).body());
    assertEquals(
        Map.of("category", "history", "title", "Bac", "periodLabel", "2007", "periodYear", 2007),
        new ProfileItem("history", "Bac", null, "2007", 2007).body());
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
