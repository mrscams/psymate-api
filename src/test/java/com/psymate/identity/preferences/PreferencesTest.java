package com.psymate.identity.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Merge RFC 7396 et domaines des préférences : table de cas. */
class PreferencesTest {

  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static final String DUR = "sessions.defaultDurationMin";
  private static final String MED = "entryTypes.medicationVisible";

  static List<Arguments> cases() {
    // stocké, patch, stocké attendu, clés changées, clés fautives
    return List.of(
        Arguments.of(
            map("theme", "light"),
            map("theme", "dark"),
            map("theme", "dark"),
            List.of("theme"),
            List.of()),
        Arguments.of(
            map("locale", "en"),
            map("theme", "dark"),
            map("locale", "en", "theme", "dark"),
            List.of("theme"),
            List.of()),
        Arguments.of(map("theme", "dark"), map("theme", null), map(), List.of("theme"), List.of()),
        Arguments.of(map(), map("theme", null), map(), List.of(), List.of()),
        Arguments.of(
            map("theme", "dark"), map("theme", "dark"), map("theme", "dark"), List.of(), List.of()),
        Arguments.of(map(), map("foo", 1), map(), List.of(), List.of()),
        Arguments.of(map(), map("foo", null), map(), List.of(), List.of()),
        Arguments.of(
            map(), map("overview.window", "7d"), map(), List.of(), List.of("overview.window")),
        Arguments.of(
            map(),
            map("overview.view", "complete", "overview.window", "12m"),
            map("overview.view", "complete", "overview.window", "12m"),
            List.of("overview.view", "overview.window"),
            List.of()),
        Arguments.of(map(), map(DUR, 14), map(), List.of(), List.of(DUR)),
        Arguments.of(map(), map(DUR, 181), map(), List.of(), List.of(DUR)),
        Arguments.of(map(), map(DUR, 15), map(DUR, 15), List.of(DUR), List.of()),
        Arguments.of(map(), map(DUR, 180), map(DUR, 180), List.of(DUR), List.of()),
        Arguments.of(map(), map(DUR, 90.5), map(), List.of(), List.of(DUR)),
        Arguments.of(map(), map(DUR, "60"), map(), List.of(), List.of(DUR)),
        Arguments.of(map(), map(MED, "true"), map(), List.of(), List.of(MED)),
        Arguments.of(map(), map(MED, true), map(MED, true), List.of(MED), List.of()),
        Arguments.of(
            map(),
            map("entries.defaultVisibility", "public"),
            map(),
            List.of(),
            List.of("entries.defaultVisibility")),
        Arguments.of(
            map(),
            map("locale", "de", "theme", "dark"),
            map("theme", "dark"),
            List.of("theme"),
            List.of("locale")),
        Arguments.of(
            map(),
            map("vocabulary.subjectLabel", "client", "timeline.groupBy", "month"),
            map("vocabulary.subjectLabel", "client", "timeline.groupBy", "month"),
            List.of("vocabulary.subjectLabel", "timeline.groupBy"),
            List.of()));
  }

  @ParameterizedTest
  @MethodSource("cases")
  void mergeTable(
      Map<String, Object> stored,
      Map<String, Object> patch,
      Map<String, Object> expectedStored,
      List<String> changed,
      List<String> faulty) {
    Preferences.MergeResult r = Preferences.merge(stored, patch);
    assertThat(r.violations())
        .extracting(Preferences.Violation::key)
        .containsExactlyInAnyOrderElementsOf(faulty);
    if (faulty.isEmpty()) {
      assertThat(r.stored()).isEqualTo(expectedStored);
    }
    assertThat(r.changedKeys()).containsExactlyInAnyOrderElementsOf(changed);
  }

  @Test
  void defaultsDependOnProfession() {
    assertThat(Preferences.defaults("psychologist"))
        .containsEntry("vocabulary.subjectLabel", "patient")
        .containsEntry(MED, false)
        .containsEntry(DUR, 60)
        .containsEntry("locale", "fr")
        .hasSize(9);
    assertThat(Preferences.defaults("coach"))
        .containsEntry("vocabulary.subjectLabel", "client")
        .containsEntry(MED, false);
    assertThat(Preferences.defaults("psychiatrist")).containsEntry(MED, true);
  }

  @Test
  void effectiveCompletesStoredAndDropsUnknownOrInvalid() {
    Map<String, Object> eff =
        Preferences.effective("coach", map("theme", "dark", "foo", 1, "locale", "xx"));
    assertThat(eff)
        .containsEntry("theme", "dark")
        .containsEntry("locale", "fr")
        .doesNotContainKey("foo")
        .hasSize(9);
  }
}
