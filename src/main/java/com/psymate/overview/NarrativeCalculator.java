package com.psymate.overview;

import com.psymate.overview.NarrativeFacts.Dominant;
import com.psymate.overview.NarrativeFacts.Trend;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calculs purs des données du résumé : tendance sur deux fois 30 jours, ressenti dominant, rythme
 * des séances. Aucun accès à la base ni à une locale.
 */
final class NarrativeCalculator {

  /** Longueur de chacune des deux fenêtres comparées, en jours. */
  static final int WINDOW_DAYS = 30;

  /** Nombre minimal de séances pour parler de rythme. */
  static final int MIN_SESSIONS_FOR_RHYTHM = 3;

  private NarrativeCalculator() {}

  /** Un ressenti non archivé : son jour UTC, son émotion et son intensité. */
  record Sample(LocalDate day, String code, String labelFr, String labelEn, int intensity) {}

  /** Tendance : moyenne de J-29 à J contre moyenne de J-59 à J-30 ({@code today} = J, inclus). */
  static Trend trend(LocalDate today, List<Sample> samples) {
    LocalDate recentStart = today.minusDays(WINDOW_DAYS - 1L);
    LocalDate previousStart = today.minusDays(2L * WINDOW_DAYS - 1);
    long recentSum = 0;
    int recentCount = 0;
    long previousSum = 0;
    int previousCount = 0;
    for (Sample s : samples) {
      if (inRange(s.day(), recentStart, today)) {
        recentSum += s.intensity();
        recentCount++;
      } else if (inRange(s.day(), previousStart, recentStart.minusDays(1))) {
        previousSum += s.intensity();
        previousCount++;
      }
    }
    return Trend.of(
        recentCount == 0 ? null : (double) recentSum / recentCount,
        previousCount == 0 ? null : (double) previousSum / previousCount,
        recentCount,
        previousCount);
  }

  private record Ranked(Dominant dominant, double avg) {}

  /**
   * Émotion la plus fréquente de J-29 à J ; à égalité, l'intensité moyenne la plus haute, puis le
   * code. Vide sans ressenti.
   */
  static Optional<Dominant> dominant(LocalDate today, List<Sample> samples) {
    LocalDate start = today.minusDays(WINDOW_DAYS - 1L);
    Map<String, List<Sample>> byCode = new HashMap<>();
    for (Sample s : samples) {
      if (inRange(s.day(), start, today)) {
        byCode.computeIfAbsent(s.code(), k -> new ArrayList<>()).add(s);
      }
    }
    return byCode.entrySet().stream()
        .map(
            e -> {
              Sample first = e.getValue().get(0);
              double avg = e.getValue().stream().mapToInt(Sample::intensity).average().orElse(0);
              return new Ranked(
                  new Dominant(e.getKey(), first.labelFr(), first.labelEn(), e.getValue().size()),
                  avg);
            })
        .min(
            Comparator.comparingInt((Ranked r) -> -r.dominant().count())
                .thenComparingDouble(r -> -r.avg())
                .thenComparing(r -> r.dominant().code()))
        .map(Ranked::dominant);
  }

  /**
   * Intervalle médian, arrondi au jour, entre les séances consécutives (instants fournis dans
   * n'importe quel ordre) ; nul s'il y a moins de {@link #MIN_SESSIONS_FOR_RHYTHM} séances.
   */
  static Integer rhythmDays(List<Instant> sessionStarts) {
    if (sessionStarts.size() < MIN_SESSIONS_FOR_RHYTHM) {
      return null;
    }
    List<Instant> sorted = sessionStarts.stream().sorted().toList();
    List<Double> gaps = new ArrayList<>();
    for (int i = 1; i < sorted.size(); i++) {
      gaps.add(Duration.between(sorted.get(i - 1), sorted.get(i)).toMinutes() / 1440.0);
    }
    gaps.sort(Comparator.naturalOrder());
    int n = gaps.size();
    double median = n % 2 == 1 ? gaps.get(n / 2) : (gaps.get(n / 2 - 1) + gaps.get(n / 2)) / 2;
    return (int) Math.round(median);
  }

  private static boolean inRange(LocalDate day, LocalDate from, LocalDate to) {
    return !day.isBefore(from) && !day.isAfter(to);
  }
}
