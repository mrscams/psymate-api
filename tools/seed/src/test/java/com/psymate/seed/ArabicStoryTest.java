package com.psymate.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.psymate.seed.Plan.CloseSession;
import com.psymate.seed.Plan.CreateEntry;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.Plan.Practitioner;
import com.psymate.seed.Plan.StartSession;
import com.psymate.seed.Plan.Step;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * La démonstration en arabe (M16-06) sans réseau : le plan {@link ArabicStory} respecte E, porte au
 * moins trois types d'entrée en arabe et une entrée mixte ; le compte est neuf ; les décisions de
 * {@link ArabicSeeder} (préférence, titres relus, catalogues) sont justes.
 */
class ArabicStoryTest {

  private static final Instant TODAY = Instant.parse("2026-11-23T00:00:00Z");
  private static final Pattern ARABIC = Pattern.compile("\\p{IsArabic}");
  private static final Pattern LATIN = Pattern.compile("\\p{IsLatin}");
  private static final Pattern ARABIC_INDIC_DIGIT =
      Pattern.compile("[\\u0660-\\u0669\\u06F0-\\u06F9]");

  private static List<CreateEntry> entries(PatientPlan plan) {
    List<CreateEntry> entries = new ArrayList<>();
    for (Step step : plan.steps()) {
      if (step instanceof CreateEntry e) {
        entries.add(e);
      }
    }
    return entries;
  }

  private static String text(CreateEntry e, String key) {
    return e.body().get(key) instanceof String s ? s : "";
  }

  @Test
  void planRespectsTheRulesOfEAndHasAStableName() {
    PatientPlan plan = ArabicStory.build(TODAY);
    assertEquals("SEED · Youssef A.", plan.displayName());
    assertEquals(List.of(), Scenario.validate(new Plan(List.of(plan))));
    assertNull(plan.practitioner(), "hors de l'énumération Practitioner");
    assertTrue(plan.profile().isEmpty());
    List<StartSession> sessions =
        plan.steps().stream()
            .filter(StartSession.class::isInstance)
            .map(StartSession.class::cast)
            .toList();
    assertEquals(3, sessions.size());
    sessions.forEach(s -> assertTrue(s.occurredFrom().isBefore(TODAY), "séance passée " + s));
    for (CreateEntry e : entries(plan)) {
      assertTrue(e.sessionRef() != null && !e.sessionRef().isBlank(), "séance obligatoire");
      assertFalse(e.body().containsKey("authorType") || e.body().containsKey("submissionId"));
    }
  }

  @Test
  void atLeastThreeEntryTypesHaveTheirTitleAndTextInArabic() {
    Set<String> arabicTypes = new TreeSet<>();
    int arabicEntries = 0;
    for (CreateEntry e : entries(ArabicStory.build(TODAY))) {
      String title = text(e, "title");
      String body = text(e, "body");
      if (ARABIC.matcher(title).find()
          && !LATIN.matcher(title).find()
          && ARABIC.matcher(body).find()
          && !LATIN.matcher(body).find()) {
        arabicTypes.add((String) e.body().get("type"));
        arabicEntries++;
      }
    }
    assertEquals(Set.of("event", "feeling", "note", "situation"), arabicTypes);
    assertEquals(8, arabicEntries);
  }

  @Test
  void oneNoteMixesArabicAndFrenchInTheSameSentence() {
    List<CreateEntry> mixed =
        entries(ArabicStory.build(TODAY)).stream()
            .filter(e -> ArabicStory.MIXED_TITLE.equals(e.body().get("title")))
            .toList();
    assertEquals(1, mixed.size());
    assertEquals("note", mixed.get(0).body().get("type"));
    String body = text(mixed.get(0), "body");
    // une seule phrase : aucun point avant le point final
    assertEquals(body.length() - 1, body.indexOf('.'), body);
    assertTrue(ARABIC.matcher(body).find() && LATIN.matcher(body).find(), body);
  }

  @Test
  void arabicTextKeepsLatinDigits() {
    for (Step step : ArabicStory.build(TODAY).steps()) {
      String text =
          switch (step) {
            case CreateEntry e -> text(e, "title") + " " + text(e, "body");
            case CloseSession c -> c.summary();
            default -> "";
          };
      assertFalse(ARABIC_INDIC_DIGIT.matcher(text).find(), "chiffres latins (Q101) : " + text);
    }
  }

  @Test
  void sessionsAreClosedWithAnArabicSummary() {
    List<CloseSession> closes =
        ArabicStory.build(TODAY).steps().stream()
            .filter(CloseSession.class::isInstance)
            .map(CloseSession.class::cast)
            .toList();
    assertEquals(3, closes.size());
    closes.forEach(c -> assertTrue(ARABIC.matcher(c.summary()).find(), c.summary()));
  }

  @Test
  void sameDaySamePlan() {
    assertEquals(ArabicStory.build(TODAY), ArabicStory.build(TODAY.plusSeconds(3600)));
    assertNotEquals(ArabicStory.build(TODAY), ArabicStory.build(TODAY.plusSeconds(7 * 24 * 3600)));
  }

  // ---- compte

  @Test
  void arabicAccountIsANewSeedAccountRegisteredInArabic() {
    Accounts.Account ar = Accounts.ARABIC;
    assertEquals("nour.seed@example.test", ar.email());
    assertEquals("ar", ar.language());
    assertNull(ar.who());
    for (Practitioner who : Practitioner.values()) {
      assertNotEquals(Accounts.of(who).email(), ar.email(), "compte e2e en français : " + who);
      assertEquals("fr", Accounts.of(who).language(), "rien ne change pour " + who);
    }
  }

  // ---- décisions de ArabicSeeder

  @Test
  void localeIsPatchedOnlyWhenItIsNotArabicYet() {
    assertTrue(ArabicSeeder.needsLocale(Map.of("locale", "fr")));
    assertTrue(ArabicSeeder.needsLocale(Map.of()));
    assertFalse(ArabicSeeder.needsLocale(Map.of("locale", "ar")));
    assertEquals(Map.of("locale", "ar"), ArabicSeeder.localePatch());
  }

  @Test
  void missingTitlesAreThoseNotReadBackFromTheTimeline() {
    PatientPlan plan = ArabicStory.build(TODAY);
    List<String> titles = ArabicSeeder.titles(plan);
    assertEquals(9, titles.size());
    assertEquals(List.of(), ArabicSeeder.missingTitles(plan, titles));
    List<String> seen = new ArrayList<>(titles);
    seen.remove("سبب الاستشارة");
    assertEquals(List.of("سبب الاستشارة"), ArabicSeeder.missingTitles(plan, seen));
  }

  @Test
  void cataloguesNeedLabelArAndInverseLabelArWhereTheyApply() {
    List<Map<String, Object>> emotions =
        List.of(
            map("code", "anxiety", "scope", "global", "labelAr", "قلق"),
            map("code", "mine", "scope", "custom", "labelAr", null));
    List<Map<String, Object>> entryTypes = List.of(map("code", "note", "labelAr", "ملاحظة"));
    List<Map<String, Object>> linkTypes =
        List.of(
            map(
                "code",
                "triggers",
                "directed",
                true,
                "labelAr",
                "يُثير",
                "inverseLabelAr",
                "يُثيره"),
            map("code", "co_occurs", "directed", false, "labelAr", "يتزامن مع"));
    ArabicSeeder.Catalogues ok = ArabicSeeder.catalogues(emotions, entryTypes, linkTypes);
    assertEquals(List.of(), ok.problems());
    assertEquals(1, ok.emotions());
    assertEquals(1, ok.directedLinkTypes());

    ArabicSeeder.Catalogues missing =
        ArabicSeeder.catalogues(
            List.of(map("code", "joy", "scope", "global", "labelAr", " ")),
            List.of(map("code", "event")),
            List.of(map("code", "follows", "directed", true, "labelAr", "يلي")));
    assertEquals(
        List.of(
            "émotion joy sans labelAr",
            "type d'entrée event sans labelAr",
            "type de lien follows sans inverseLabelAr"),
        missing.problems());
    assertEquals(
        List.of("catalogue vide"),
        ArabicSeeder.catalogues(List.of(), List.of(), List.of()).problems());
  }

  private static Map<String, Object> map(Object... keyValues) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      m.put((String) keyValues[i], keyValues[i + 1]);
    }
    return m;
  }
}
