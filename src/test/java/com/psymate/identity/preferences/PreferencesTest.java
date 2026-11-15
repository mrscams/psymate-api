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
        Arguments.of(
            map(),
            map(DUR, new java.math.BigInteger("18446744073709551676")),
            map(),
            List.of(),
            List.of(DUR)),
        Arguments.of(
            map(), map(DUR, new java.math.BigDecimal("60")), map(), List.of(), List.of(DUR)),
        Arguments.of(map(), map(DUR, 60L), map(DUR, 60), List.of(DUR), List.of()),
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
            List.of()),
        // Agenda (Q57) : bornes de la journée.
        Arguments.of(map(), map(START, 0), map(START, 0), List.of(START), List.of()),
        Arguments.of(
            map(),
            map(START, 23, END, 24),
            map(START, 23, END, 24),
            List.of(START, END),
            List.of()),
        Arguments.of(map(), map(START, -1), map(), List.of(), List.of(START)),
        Arguments.of(map(), map(START, 24), map(), List.of(), List.of(START)),
        Arguments.of(map(), map(START, "8"), map(), List.of(), List.of(START)),
        Arguments.of(map(), map(END, 0), map(), List.of(), List.of(END)),
        Arguments.of(map(), map(END, 25), map(), List.of(), List.of(END)),
        Arguments.of(map(), map(END, 7L), map(), List.of(), List.of(END)),
        Arguments.of(map(START, 8), map(END, 8), map(), List.of(), List.of(END)),
        Arguments.of(map(END, 19), map(START, 19), map(), List.of(), List.of(END)),
        Arguments.of(map(END, 19), map(START, 20), map(), List.of(), List.of(END)),
        Arguments.of(map(START, 20), map(START, null), map(), List.of(START), List.of()),
        Arguments.of(
            map(START, 10, END, 12), map(END, null), map(START, 10), List.of(END), List.of()),
        Arguments.of(
            map(START, 12, END, 14), map(START, null), map(END, 14), List.of(START), List.of()),
        Arguments.of(
            map(START, 20, END, 22), map(END, null), map(START, 20), List.of(), List.of(END)),
        // Agenda : jours travaillés.
        Arguments.of(
            map(), map(DAYS, List.of(6, 7)), map(DAYS, List.of(6, 7)), List.of(DAYS), List.of()),
        Arguments.of(
            map(), map(DAYS, List.of(1L, 2L)), map(DAYS, List.of(1, 2)), List.of(DAYS), List.of()),
        Arguments.of(map(), map(DAYS, List.of(1, 1, 8)), map(), List.of(), List.of(DAYS)),
        Arguments.of(map(), map(DAYS, List.of(1, 1)), map(), List.of(), List.of(DAYS)),
        Arguments.of(map(), map(DAYS, List.of(0)), map(), List.of(), List.of(DAYS)),
        Arguments.of(map(), map(DAYS, List.of()), map(), List.of(), List.of(DAYS)),
        Arguments.of(map(), map(DAYS, List.of("1")), map(), List.of(), List.of(DAYS)),
        Arguments.of(map(), map(DAYS, "1,2"), map(), List.of(), List.of(DAYS)),
        Arguments.of(
            map(DAYS, List.of(1, 2)),
            map(DAYS, List.of(1, 2)),
            map(DAYS, List.of(1, 2)),
            List.of(),
            List.of()),
        // Agenda : vue par défaut.
        Arguments.of(map(), map(VIEW, "list"), map(VIEW, "list"), List.of(VIEW), List.of()),
        Arguments.of(map(), map(VIEW, "year"), map(), List.of(), List.of(VIEW)));
  }

  private static final String START = "agenda.dayStartHour";
  private static final String END = "agenda.dayEndHour";
  private static final String DAYS = "agenda.workingDays";
  private static final String VIEW = "agenda.defaultView";

  @Test
  void dayEndNotAfterDayStartIsOutOfRangeOnDayEndHour() {
    Preferences.MergeResult r = Preferences.merge(map(), map(END, 7));
    assertThat(r.violations()).containsExactly(new Preferences.Violation(END, "OUT_OF_RANGE"));
    assertThat(Preferences.merge(map(), map(END, 25)).violations())
        .containsExactly(new Preferences.Violation(END, "OUT_OF_RANGE"));
    assertThat(Preferences.merge(map(), map(END, "19")).violations())
        .containsExactly(new Preferences.Violation(END, "INVALID_VALUE"));
    assertThat(Preferences.merge(map(), map(DAYS, List.of(1, 1, 8))).violations())
        .containsExactly(new Preferences.Violation(DAYS, "INVALID_VALUE"));
    assertThat(Preferences.merge(map(), map(DAYS, List.of(8))).violations())
        .containsExactly(new Preferences.Violation(DAYS, "OUT_OF_RANGE"));
    assertThat(Preferences.merge(map(), map(VIEW, "year")).violations())
        .containsExactly(new Preferences.Violation(VIEW, "INVALID_VALUE"));
    // Un PATCH qui ne touche pas aux bornes n'est jamais rejeté pour elles.
    assertThat(Preferences.merge(map(START, 20, END, 19), map("theme", "dark")).violations())
        .isEmpty();
  }

  @Test
  void effectiveRestoresDefaultBoundsWhenStoredBoundsAreInconsistent() {
    assertThat(Preferences.effective("coach", map(START, 20, END, 19)))
        .containsEntry(START, 8)
        .containsEntry(END, 19);
    assertThat(Preferences.effective("coach", map(START, 7, END, 20L, DAYS, List.of(7L))))
        .containsEntry(START, 7)
        .containsEntry(END, 20)
        .containsEntry(DAYS, List.of(7));
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
        .containsEntry(START, 8)
        .containsEntry(END, 19)
        .containsEntry(DAYS, List.of(1, 2, 3, 4, 5, 6))
        .containsEntry(VIEW, "week")
        .hasSize(13);
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
        .hasSize(13);
  }
}
