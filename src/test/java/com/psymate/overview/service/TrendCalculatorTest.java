package com.psymate.overview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.overview.dto.EmotionsDto;
import com.psymate.overview.dto.TrendPointDto;
import com.psymate.overview.model.OverviewWindow;
import com.psymate.overview.service.TrendCalculator.FeelingSample;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Moyenne glissante sur 7 jours et émotions dominantes (logique pure, sans base). */
class TrendCalculatorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

  private static FeelingSample sample(int daysAgo, String code, int intensity, Integer valence) {
    return new FeelingSample(TODAY.minusDays(daysAgo), code, "Label " + code, intensity, valence);
  }

  private static TrendPointDto pointAt(List<TrendPointDto> points, int daysAgo) {
    return points.stream()
        .filter(p -> p.date().equals(TODAY.minusDays(daysAgo)))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void rollingAverageCoversTheSevenDaysEndingAtEachDate() {
    List<FeelingSample> samples =
        List.of(sample(3, "anxiety", 8, -1), sample(0, "sadness", 2, -2), sample(10, "joy", 4, 2));

    List<TrendPointDto> points =
        TrendCalculator.points(OverviewWindow.D30.pointDates(TODAY), samples);

    assertThat(points).hasSize(30);
    // Aujourd'hui : les jours -6 à 0 contiennent le 8 (J-3) et le 2 (J-0).
    assertThat(pointAt(points, 0).averageIntensity()).isEqualTo(5.0);
    assertThat(pointAt(points, 0).valence()).isEqualTo(-1.5);
    // J-3 : seul le 8 est dans la fenêtre glissante.
    assertThat(pointAt(points, 3).averageIntensity()).isEqualTo(8.0);
    // J-10 : seul le 4 est dans les jours -16 à -10.
    assertThat(pointAt(points, 10).averageIntensity()).isEqualTo(4.0);
  }

  @Test
  void aPointWithNoFeelingInItsRollingWindowIsNull() {
    List<FeelingSample> samples = List.of(sample(20, "joy", 6, null));

    List<TrendPointDto> points =
        TrendCalculator.points(OverviewWindow.D30.pointDates(TODAY), samples);

    assertThat(pointAt(points, 20).averageIntensity()).isEqualTo(6.0);
    assertThat(pointAt(points, 14).averageIntensity()).isEqualTo(6.0);
    assertThat(pointAt(points, 13).averageIntensity()).isNull();
    assertThat(pointAt(points, 0).averageIntensity()).isNull();
    assertThat(pointAt(points, 0).valence()).isNull();
  }

  @Test
  void valenceAveragesOnlyTheKnownValences() {
    List<FeelingSample> samples = List.of(sample(0, "a", 5, 2), sample(0, "b", 7, null));

    TrendPointDto today = pointAt(TrendCalculator.points(List.of(TODAY), samples), 0);

    assertThat(today.averageIntensity()).isEqualTo(6.0);
    assertThat(today.valence()).isEqualTo(2.0);
  }

  @Test
  void averagesAreRoundedToTwoDecimals() {
    List<FeelingSample> samples =
        List.of(sample(0, "a", 1, null), sample(0, "a", 1, null), sample(0, "a", 2, null));

    assertThat(TrendCalculator.points(List.of(TODAY), samples).get(0).averageIntensity())
        .isEqualTo(1.33);
  }

  @Test
  void topEmotionsAreSortedCountedAndComparedToThePreviousWindow() {
    LocalDate start = OverviewWindow.D30.start(TODAY);
    LocalDate previousStart = OverviewWindow.D30.previousStart(TODAY);
    List<FeelingSample> samples = new ArrayList<>();
    samples.add(sample(1, "anxiety", 5, null));
    samples.add(sample(2, "anxiety", 5, null));
    samples.add(sample(3, "anxiety", 5, null));
    samples.add(sample(4, "joy", 5, null));
    samples.add(sample(5, "joy", 5, null));
    samples.add(sample(6, "anger", 5, null));
    samples.add(sample(40, "anxiety", 5, null)); // fenêtre précédente (J-59 à J-30)
    samples.add(sample(41, "joy", 5, null));
    samples.add(sample(42, "joy", 5, null));
    samples.add(sample(43, "joy", 5, null));
    samples.add(sample(100, "anxiety", 5, null)); // avant la fenêtre précédente

    EmotionsDto emotions = TrendCalculator.emotions(previousStart, start, TODAY, samples);

    assertThat(emotions.top()).extracting("code").containsExactly("anxiety", "joy", "anger");
    assertThat(emotions.top().get(0).count()).isEqualTo(3);
    assertThat(emotions.top().get(0).previousCount()).isEqualTo(1);
    assertThat(emotions.top().get(0).delta()).isEqualTo(2);
    assertThat(emotions.top().get(1).delta()).isEqualTo(-1);
    assertThat(emotions.top().get(2).previousCount()).isZero();
    assertThat(emotions.top().get(0).label()).isEqualTo("Label anxiety");
  }

  @Test
  void topEmotionsAreLimitedToFiveAndTiesBreakOnTheCode() {
    List<FeelingSample> samples = new ArrayList<>();
    for (String code : List.of("g", "f", "e", "d", "c", "b", "a")) {
      samples.add(sample(1, code, 5, null));
    }

    EmotionsDto emotions =
        TrendCalculator.emotions(
            OverviewWindow.D30.previousStart(TODAY),
            OverviewWindow.D30.start(TODAY),
            TODAY,
            samples);

    assertThat(emotions.top()).extracting("code").containsExactly("a", "b", "c", "d", "e");
  }

  @Test
  void anEmptyWindowGivesAnEmptyTopAndNullFreePoints() {
    assertThat(
            TrendCalculator.emotions(
                    OverviewWindow.D90.previousStart(TODAY),
                    OverviewWindow.D90.start(TODAY),
                    TODAY,
                    List.of())
                .top())
        .isEmpty();
    assertThat(TrendCalculator.points(OverviewWindow.D90.pointDates(TODAY), List.of()))
        .hasSize(90)
        .allMatch(p -> p.averageIntensity() == null && p.valence() == null);
  }
}
