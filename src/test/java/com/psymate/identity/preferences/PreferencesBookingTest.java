package com.psymate.identity.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** M14-02 : clés booking.* (Q75), défauts, validation des plages et des réglages, tri. */
class PreferencesBookingTest {

  private static final String ENABLED = "booking.enabled";
  private static final String CONFIRMATION = "booking.confirmation";
  private static final String WINDOWS = "booking.windows";
  private static final String SLOT = "booking.slotDurationMin";
  private static final String NOTICE = "booking.minNoticeHours";
  private static final String HORIZON = "booking.horizonDays";
  private static final String MODALITIES = "booking.modalities";
  private static final String TIMEZONE = "booking.timezone";

  private static final String INVALID = "INVALID_VALUE";
  private static final String OUT = "OUT_OF_RANGE";

  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static Map<String, Object> w(Object day, Object start, Object end) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("day", day);
    m.put("start", start);
    m.put("end", end);
    return m;
  }

  private static String code(String key, Object value) {
    return Preferences.violation(key, value);
  }

  @Test
  void defaultsHoldTheEightBookingKeys() {
    Map<String, Object> d = Preferences.defaults("psychologist");
    assertThat(d)
        .containsEntry(ENABLED, false)
        .containsEntry(CONFIRMATION, "manual")
        .containsEntry(WINDOWS, List.of())
        .containsEntry(SLOT, 60)
        .containsEntry(NOTICE, 24)
        .containsEntry(HORIZON, 28)
        .containsEntry(MODALITIES, List.of("in_person"))
        .containsEntry(TIMEZONE, "Europe/Paris")
        .hasSize(Preferences.KEY_COUNT);
    assertThat(Preferences.KEY_COUNT).isEqualTo(21);
    assertThat(Preferences.effective("coach", Map.of())).isEqualTo(Preferences.defaults("coach"));
  }

  @Test
  void enabledAndConfirmation() {
    assertThat(code(ENABLED, true)).isNull();
    assertThat(code(ENABLED, false)).isNull();
    assertThat(code(ENABLED, "true")).isEqualTo(INVALID);
    assertThat(code(ENABLED, 1)).isEqualTo(INVALID);
    assertThat(code(CONFIRMATION, "manual")).isNull();
    assertThat(code(CONFIRMATION, "auto")).isNull();
    assertThat(code(CONFIRMATION, "automatic")).isEqualTo(INVALID);
    assertThat(code(CONFIRMATION, "AUTO")).isEqualTo(INVALID);
    assertThat(code(CONFIRMATION, true)).isEqualTo(INVALID);
  }

  static Stream<Arguments> windowCases() {
    return Stream.of(
        // Valides.
        Arguments.of(List.of(), null),
        Arguments.of(List.of(w(1, "09:00", "12:00")), null),
        Arguments.of(List.of(w(7, "00:00", "23:55")), null),
        Arguments.of(List.of(w(1L, "09:05", "09:10")), null),
        Arguments.of(List.of(w(1, "09:00", "12:00"), w(1, "12:00", "14:00")), null),
        Arguments.of(List.of(w(1, "09:00", "12:00"), w(2, "11:00", "13:00")), null),
        Arguments.of(List.of(w(1, "14:00", "18:00"), w(1, "09:00", "12:00")), null),
        // Type de la valeur.
        Arguments.of("09:00-12:00", INVALID),
        Arguments.of(Map.of("day", 1), INVALID),
        Arguments.of(List.of("lundi"), INVALID),
        Arguments.of(List.of(List.of(1, "09:00", "12:00")), INVALID),
        // Jour.
        Arguments.of(List.of(w(0, "09:00", "12:00")), INVALID),
        Arguments.of(List.of(w(8, "09:00", "12:00")), INVALID),
        Arguments.of(List.of(w("1", "09:00", "12:00")), INVALID),
        Arguments.of(List.of(w(1.0, "09:00", "12:00")), INVALID),
        Arguments.of(List.of(w(null, "09:00", "12:00")), INVALID),
        // Format HH:mm strict, minutes multiples de 5.
        Arguments.of(List.of(w(1, "9:00", "12:00")), INVALID),
        Arguments.of(List.of(w(1, "09:00", "12:0")), INVALID),
        Arguments.of(List.of(w(1, "09h00", "12:00")), INVALID),
        Arguments.of(List.of(w(1, "09:00:00", "12:00")), INVALID),
        Arguments.of(List.of(w(1, "09:00", "24:00")), INVALID),
        Arguments.of(List.of(w(1, "09:60", "12:00")), INVALID),
        Arguments.of(List.of(w(1, "09:07", "12:00")), INVALID),
        Arguments.of(List.of(w(1, "09:00", "12:03")), INVALID),
        Arguments.of(List.of(w(1, " 09:00", "12:00")), INVALID),
        Arguments.of(List.of(w(1, 900, "12:00")), INVALID),
        // Fin strictement après le début.
        Arguments.of(List.of(w(1, "12:00", "12:00")), INVALID),
        Arguments.of(List.of(w(1, "12:00", "09:00")), INVALID),
        // Chevauchement le même jour, quel que soit l'ordre d'envoi.
        Arguments.of(List.of(w(1, "09:00", "12:00"), w(1, "11:00", "13:00")), INVALID),
        Arguments.of(List.of(w(1, "11:00", "13:00"), w(1, "09:00", "12:00")), INVALID),
        Arguments.of(List.of(w(3, "09:00", "18:00"), w(3, "10:00", "11:00")), INVALID),
        Arguments.of(List.of(w(3, "09:00", "12:00"), w(3, "09:00", "12:00")), INVALID));
  }

  @ParameterizedTest
  @MethodSource("windowCases")
  void windowsValidation(Object windows, String expected) {
    assertThat(code(WINDOWS, windows)).isEqualTo(expected);
  }

  @Test
  void windowObjectNeedsExactlyDayStartEnd() {
    Map<String, Object> missing = new LinkedHashMap<>();
    missing.put("day", 1);
    missing.put("start", "09:00");
    assertThat(code(WINDOWS, List.of(missing))).isEqualTo(INVALID);
    Map<String, Object> extra = w(1, "09:00", "12:00");
    extra.put("label", "matin");
    assertThat(code(WINDOWS, List.of(extra))).isEqualTo(INVALID);
    Map<String, Object> renamed = new LinkedHashMap<>();
    renamed.put("weekday", 1);
    renamed.put("start", "09:00");
    renamed.put("end", "12:00");
    assertThat(code(WINDOWS, List.of(renamed))).isEqualTo(INVALID);
    List<Object> withNull = new ArrayList<>();
    withNull.add(null);
    assertThat(code(WINDOWS, withNull)).isEqualTo(INVALID);
  }

  @Test
  void atMostTwentyOneWindows() {
    List<Map<String, Object>> windows = new ArrayList<>();
    for (int day = 1; day <= 7; day++) {
      windows.add(w(day, "08:00", "10:00"));
      windows.add(w(day, "10:00", "12:00"));
      windows.add(w(day, "14:00", "18:00"));
    }
    assertThat(windows).hasSize(21);
    assertThat(code(WINDOWS, windows)).isNull();
    windows.add(w(1, "19:00", "20:00"));
    assertThat(code(WINDOWS, windows)).isEqualTo(OUT);
  }

  @Test
  void windowsAreStoredSortedByDayThenStartAsDayStartEndObjects() {
    Map<String, Object> second = new LinkedHashMap<>();
    second.put("end", "12:00");
    second.put("start", "09:00");
    second.put("day", 2L);
    Preferences.MergeResult r =
        Preferences.merge(
            map(),
            map(
                WINDOWS,
                List.of(
                    w(5, "09:00", "10:00"),
                    w(2, "14:00", "18:00"),
                    second,
                    w(1, "08:00", "09:00"))));
    assertThat(r.violations()).isEmpty();
    assertThat(r.changedKeys()).containsExactly(WINDOWS);
    List<Object> stored = new ArrayList<>((List<?>) r.stored().get(WINDOWS));
    assertThat(stored)
        .containsExactly(
            w(1, "08:00", "09:00"),
            w(2, "09:00", "12:00"),
            w(2, "14:00", "18:00"),
            w(5, "09:00", "10:00"));
    // Champs dans l'ordre day, start, end ; day ramené à Integer.
    List<Object> fields = new ArrayList<>(((Map<?, ?>) stored.get(1)).keySet());
    assertThat(fields).containsExactly("day", "start", "end");
    assertThat(((Map<?, ?>) stored.get(1)).get("day")).isInstanceOf(Integer.class);
    // Relu (effective) trié et identique.
    assertThat(Preferences.effective("psychologist", r.stored()).get(WINDOWS)).isEqualTo(stored);
  }

  @Test
  void sameWindowsInAnotherOrderAreNotAChange() {
    Map<String, Object> stored =
        map(WINDOWS, List.of(w(1, "09:00", "12:00"), w(2, "09:00", "12:00")));
    Preferences.MergeResult r =
        Preferences.merge(
            stored, map(WINDOWS, List.of(w(2, "09:00", "12:00"), w(1, "09:00", "12:00"))));
    assertThat(r.violations()).isEmpty();
    assertThat(r.changedKeys()).isEmpty();
  }

  @Test
  void effectiveSortsAndKeepsValidStoredWindowsButDropsInvalidOnes() {
    assertThat(
            Preferences.effective(
                    "psychologist",
                    map(WINDOWS, List.of(w(3, "14:00", "15:00"), w(3, "09:00", "10:00"))))
                .get(WINDOWS))
        .isEqualTo(List.of(w(3, "09:00", "10:00"), w(3, "14:00", "15:00")));
    Map<String, Object> eff =
        Preferences.effective(
            "psychologist",
            map(
                WINDOWS,
                List.of(w(1, "09:00", "12:00"), w(1, "11:00", "13:00")),
                SLOT,
                61,
                NOTICE,
                0,
                HORIZON,
                "28",
                MODALITIES,
                List.of(),
                TIMEZONE,
                "Paris",
                CONFIRMATION,
                "always",
                ENABLED,
                "yes"));
    assertThat(eff)
        .containsEntry(WINDOWS, List.of())
        .containsEntry(SLOT, 60)
        .containsEntry(NOTICE, 24)
        .containsEntry(HORIZON, 28)
        .containsEntry(MODALITIES, List.of("in_person"))
        .containsEntry(TIMEZONE, "Europe/Paris")
        .containsEntry(CONFIRMATION, "manual")
        .containsEntry(ENABLED, false)
        .hasSize(21);
  }

  @Test
  void slotDurationIsAMultipleOfFiveBetween15And180() {
    assertThat(code(SLOT, 15)).isNull();
    assertThat(code(SLOT, 180)).isNull();
    assertThat(code(SLOT, 45L)).isNull();
    assertThat(code(SLOT, 10)).isEqualTo(OUT);
    assertThat(code(SLOT, 185)).isEqualTo(OUT);
    assertThat(code(SLOT, 62)).isEqualTo(OUT);
    // Non entier : INVALID_VALUE, comme les autres entiers bornés (integralInRange).
    assertThat(code(SLOT, "60")).isEqualTo(INVALID);
    assertThat(code(SLOT, 60.5)).isEqualTo(INVALID);
    assertThat(code(SLOT, true)).isEqualTo(INVALID);
  }

  @Test
  void minNoticeAndHorizonBounds() {
    assertThat(code(NOTICE, 1)).isNull();
    assertThat(code(NOTICE, 168)).isNull();
    assertThat(code(NOTICE, 0)).isEqualTo(OUT);
    assertThat(code(NOTICE, 169)).isEqualTo(OUT);
    assertThat(code(NOTICE, "24")).isEqualTo(INVALID);
    assertThat(code(HORIZON, 7)).isNull();
    assertThat(code(HORIZON, 90)).isNull();
    assertThat(code(HORIZON, 6)).isEqualTo(OUT);
    assertThat(code(HORIZON, 91)).isEqualTo(OUT);
    assertThat(code(HORIZON, 28.0)).isEqualTo(INVALID);
  }

  @Test
  void modalitiesAreANonEmptySubsetWithoutDuplicates() {
    assertThat(code(MODALITIES, List.of("in_person"))).isNull();
    assertThat(code(MODALITIES, List.of("phone", "video", "in_person"))).isNull();
    assertThat(code(MODALITIES, List.of())).isEqualTo(INVALID);
    assertThat(code(MODALITIES, List.of("video", "video"))).isEqualTo(INVALID);
    assertThat(code(MODALITIES, List.of("visio"))).isEqualTo(INVALID);
    assertThat(code(MODALITIES, List.of(1))).isEqualTo(INVALID);
    assertThat(code(MODALITIES, "video")).isEqualTo(INVALID);
    Preferences.MergeResult r =
        Preferences.merge(map(), map(MODALITIES, List.of("video", "in_person")));
    assertThat(r.stored()).containsEntry(MODALITIES, List.of("video", "in_person"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Europe/Paris", "America/Montreal", "Asia/Tokyo", "UTC"})
  void regionalTimezonesAreAccepted(String zone) {
    assertThat(code(TIMEZONE, zone)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Paris",
        "+02:00",
        "UTC+1",
        "UTC+01:00",
        "GMT+2",
        "Z",
        "europe/paris",
        "",
        "Mars/Olympus"
      })
  void offsetsAndUnknownTimezonesAreRejected(String zone) {
    assertThat(code(TIMEZONE, zone)).isEqualTo(INVALID);
  }

  @Test
  void timezoneMustBeAString() {
    assertThat(code(TIMEZONE, 2)).isEqualTo(INVALID);
  }

  @Test
  void enabledWithoutAnyWindowIsAccepted() {
    Preferences.MergeResult r = Preferences.merge(map(), map(ENABLED, true));
    assertThat(r.violations()).isEmpty();
    assertThat(r.stored()).containsEntry(ENABLED, true);
  }

  @Test
  void oneFaultyBookingKeyIsReportedAloneWithItsCode() {
    Preferences.MergeResult r =
        Preferences.merge(
            map(),
            map(
                ENABLED,
                true,
                CONFIRMATION,
                "auto",
                SLOT,
                200,
                WINDOWS,
                List.of(w(1, "09:00", "12:00"))));
    assertThat(r.violations()).containsExactly(new Preferences.Violation(SLOT, OUT));
  }

  @Test
  void nullRestoresTheBookingDefault() {
    Preferences.MergeResult r =
        Preferences.merge(
            map(WINDOWS, List.of(w(1, "09:00", "12:00")), TIMEZONE, "America/Montreal"),
            map(WINDOWS, null, TIMEZONE, null));
    assertThat(r.stored()).isEmpty();
    assertThat(r.changedKeys()).containsExactlyInAnyOrder(WINDOWS, TIMEZONE);
  }
}
