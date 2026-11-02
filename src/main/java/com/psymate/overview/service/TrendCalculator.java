package com.psymate.overview.service;

import com.psymate.overview.dto.EmotionsDto;
import com.psymate.overview.dto.TopEmotionDto;
import com.psymate.overview.dto.TrendPointDto;
import com.psymate.overview.model.OverviewWindow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculs purs de la tendance et des émotions dominantes, sans accès à la base : moyenne glissante
 * sur 7 jours, top 5 des émotions et variation par rapport à la fenêtre précédente.
 */
final class TrendCalculator {

  static final int TOP_EMOTIONS = 5;

  private TrendCalculator() {}

  /**
   * Un ressenti non archivé et daté ({@code day} en UTC).
   *
   * @param valence nulle quand elle n'est pas connue
   */
  record FeelingSample(
      LocalDate day, String emotionCode, String emotionLabel, int intensity, Integer valence) {}

  /** Cumuls d'une journée. */
  private static final class Day {
    long intensitySum;
    int intensityCount;
    long valenceSum;
    int valenceCount;
  }

  /**
   * Un point par date demandée : moyenne des intensités (et des valences connues) des ressentis des
   * 7 jours se terminant à cette date, nulle s'il n'y en a aucun dans la fenêtre glissante.
   */
  static List<TrendPointDto> points(List<LocalDate> dates, List<FeelingSample> samples) {
    Map<LocalDate, Day> byDay = new HashMap<>();
    for (FeelingSample s : samples) {
      Day d = byDay.computeIfAbsent(s.day(), k -> new Day());
      d.intensitySum += s.intensity();
      d.intensityCount++;
      if (s.valence() != null) {
        d.valenceSum += s.valence();
        d.valenceCount++;
      }
    }
    List<TrendPointDto> points = new ArrayList<>(dates.size());
    for (LocalDate date : dates) {
      long intensitySum = 0;
      int intensityCount = 0;
      long valenceSum = 0;
      int valenceCount = 0;
      for (int k = 0; k < OverviewWindow.ROLLING_DAYS; k++) {
        Day d = byDay.get(date.minusDays(k));
        if (d != null) {
          intensitySum += d.intensitySum;
          intensityCount += d.intensityCount;
          valenceSum += d.valenceSum;
          valenceCount += d.valenceCount;
        }
      }
      points.add(
          new TrendPointDto(
              date,
              intensityCount == 0 ? null : round((double) intensitySum / intensityCount),
              valenceCount == 0 ? null : round((double) valenceSum / valenceCount)));
    }
    return points;
  }

  /**
   * Top 5 des émotions de la fenêtre {@code [start, today]}, avec l'effectif de la fenêtre
   * précédente {@code [previousStart, start)}. Tri par effectif décroissant puis par code.
   */
  static EmotionsDto emotions(
      LocalDate previousStart, LocalDate start, LocalDate today, List<FeelingSample> samples) {
    Map<String, Integer> current = new HashMap<>();
    Map<String, Integer> previous = new HashMap<>();
    Map<String, String> labels = new HashMap<>();
    for (FeelingSample s : samples) {
      if (!s.day().isBefore(start) && !s.day().isAfter(today)) {
        current.merge(s.emotionCode(), 1, Integer::sum);
        labels.put(s.emotionCode(), s.emotionLabel());
      } else if (!s.day().isBefore(previousStart) && s.day().isBefore(start)) {
        previous.merge(s.emotionCode(), 1, Integer::sum);
      }
    }
    List<TopEmotionDto> top =
        current.entrySet().stream()
            .sorted(
                Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(Map.Entry.comparingByKey()))
            .limit(TOP_EMOTIONS)
            .map(
                e -> {
                  int before = previous.getOrDefault(e.getKey(), 0);
                  return new TopEmotionDto(
                      e.getKey(),
                      labels.get(e.getKey()),
                      e.getValue(),
                      before,
                      e.getValue() - before);
                })
            .toList();
    return new EmotionsDto(top);
  }

  /** Arrondi à deux décimales. */
  static double round(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
