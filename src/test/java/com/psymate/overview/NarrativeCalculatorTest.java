package com.psymate.overview;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.overview.NarrativeCalculator.Sample;
import com.psymate.overview.NarrativeFacts.Direction;
import com.psymate.overview.NarrativeFacts.Trend;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fenêtres J-29..J et J-59..J-30, ressenti dominant et rythme médian. */
class NarrativeCalculatorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

  private static Sample s(int daysAgo, String code, int intensity) {
    return new Sample(TODAY.minusDays(daysAgo), code, code + "-fr", code + "-en", intensity);
  }

  @Test
  void windowsAreInclusiveAtTheirBoundaries() {
    List<Sample> samples =
        List.of(
            s(0, "anxiety", 8), // J : fenêtre courante
            s(29, "anxiety", 8), // J-29 : fenêtre courante
            s(30, "anxiety", 2), // J-30 : fenêtre précédente
            s(59, "anxiety", 2), // J-59 : fenêtre précédente
            s(60, "anxiety", 10)); // J-60 : hors comparaison
    Trend trend = NarrativeCalculator.trend(TODAY, samples);
    assertThat(trend.recentCount()).isEqualTo(2);
    assertThat(trend.previousCount()).isEqualTo(2);
    assertThat(trend.recentMean()).isEqualTo(8.0);
    assertThat(trend.previousMean()).isEqualTo(2.0);
    assertThat(trend.direction()).isEqualTo(Direction.UP);
  }

  @Test
  void exactlyOnePointIsStableAndJustMoreIsNot() {
    assertThat(Trend.of(5.0, 4.0, 3, 3).direction()).isEqualTo(Direction.STABLE);
    assertThat(Trend.of(3.0, 4.0, 3, 3).direction()).isEqualTo(Direction.STABLE);
    assertThat(Trend.of(5.01, 4.0, 3, 3).direction()).isEqualTo(Direction.UP);
    assertThat(Trend.of(2.99, 4.0, 3, 3).direction()).isEqualTo(Direction.DOWN);
    assertThat(Trend.of(null, 4.0, 0, 3).direction()).isNull();
    assertThat(Trend.of(4.0, null, 3, 0).direction()).isNull();
  }

  @Test
  void dominantIsMostFrequentThenHighestAverageThenCode() {
    // anxiety x2 (moy 5), joy x2 (moy 7), sadness x1 : égalité de 2, joy plus intense
    List<Sample> tie =
        List.of(
            s(1, "anxiety", 4),
            s(2, "anxiety", 6),
            s(3, "joy", 7),
            s(4, "joy", 7),
            s(5, "sadness", 10));
    assertThat(NarrativeCalculator.dominant(TODAY, tie)).get().extracting("code").isEqualTo("joy");

    // égalité de fréquence et de moyenne : le code le plus petit
    List<Sample> fullTie = List.of(s(1, "joy", 5), s(2, "anger", 5));
    assertThat(NarrativeCalculator.dominant(TODAY, fullTie))
        .get()
        .extracting("code")
        .isEqualTo("anger");

    // les ressentis hors des 30 derniers jours ne comptent pas
    List<Sample> old = List.of(s(40, "joy", 5), s(41, "joy", 5), s(2, "anger", 1));
    assertThat(NarrativeCalculator.dominant(TODAY, old))
        .get()
        .extracting("code")
        .isEqualTo("anger");
    assertThat(NarrativeCalculator.dominant(TODAY, List.of(s(40, "joy", 5)))).isEmpty();
  }

  @Test
  void rhythmIsTheMedianIntervalAndNeedsThreeSessions() {
    Instant t0 = Instant.parse("2026-01-01T10:00:00Z");
    assertThat(NarrativeCalculator.rhythmDays(List.of(t0, t0.plus(7, ChronoUnit.DAYS)))).isNull();

    List<Instant> weekly = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      weekly.add(t0.plus(7L * i, ChronoUnit.DAYS));
    }
    assertThat(NarrativeCalculator.rhythmDays(weekly)).isEqualTo(7);

    // intervalles 7, 7, 60 : la médiane (7) ignore la longue pause
    List<Instant> pause =
        List.of(
            t0.plus(67, ChronoUnit.DAYS),
            t0,
            t0.plus(7, ChronoUnit.DAYS),
            t0.plus(14, ChronoUnit.DAYS));
    assertThat(NarrativeCalculator.rhythmDays(pause)).isEqualTo(7);

    // trois séances : deux intervalles (7 et 21), médiane = 14
    assertThat(
            NarrativeCalculator.rhythmDays(
                List.of(t0, t0.plus(7, ChronoUnit.DAYS), t0.plus(28, ChronoUnit.DAYS))))
        .isEqualTo(14);
  }
}
