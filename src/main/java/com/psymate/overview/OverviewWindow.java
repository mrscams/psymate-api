package com.psymate.overview;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Fenêtre de la tendance et des émotions dominantes (paramètre {@code window}). Toutes les bornes
 * sont des jours UTC ; la fenêtre courante se termine aujourd'hui (inclus). 30d et 90d donnent un
 * point par jour (30 et 90), 12m un point par semaine (53 au plus) : jamais plus de {@link
 * #MAX_POINTS}.
 */
enum OverviewWindow {
  D30("30d", 1),
  D90("90d", 1),
  M12("12m", 7);

  static final int MAX_POINTS = 90;

  /** Longueur de la moyenne glissante de la tendance. */
  static final int ROLLING_DAYS = 7;

  /** Fenêtre par défaut quand {@code window} est absent. */
  static final OverviewWindow DEFAULT = D90;

  private final String code;
  private final int stepDays;

  OverviewWindow(String code, int stepDays) {
    this.code = code;
    this.stepDays = stepDays;
  }

  String code() {
    return code;
  }

  static Optional<OverviewWindow> parse(String code) {
    for (OverviewWindow w : values()) {
      if (w.code.equals(code)) {
        return Optional.of(w);
      }
    }
    return Optional.empty();
  }

  /** Premier jour de la fenêtre courante (inclus). */
  LocalDate start(LocalDate today) {
    return switch (this) {
      case D30 -> today.minusDays(29);
      case D90 -> today.minusDays(89);
      case M12 -> today.minusYears(1);
    };
  }

  /** Premier jour de la fenêtre précédente, de même longueur et accolée à la courante. */
  LocalDate previousStart(LocalDate today) {
    return switch (this) {
      case D30 -> start(today).minusDays(30);
      case D90 -> start(today).minusDays(90);
      case M12 -> today.minusYears(2);
    };
  }

  /** Dates des points de la courbe, croissantes, la dernière étant aujourd'hui. */
  List<LocalDate> pointDates(LocalDate today) {
    LocalDate start = start(today);
    List<LocalDate> dates = new ArrayList<>();
    for (LocalDate d = today; !d.isBefore(start); d = d.minusDays(stepDays)) {
      dates.add(d);
    }
    Collections.reverse(dates);
    return dates;
  }

  /** Premier jour dont une entrée peut influencer un calcul : fenêtre précédente, tous blocs. */
  static LocalDate earliestNeeded(LocalDate today) {
    LocalDate earliest = today;
    for (OverviewWindow w : values()) {
      LocalDate d = w.previousStart(today);
      if (d.isBefore(earliest)) {
        earliest = d;
      }
    }
    return earliest.minusDays(ROLLING_DAYS);
  }
}
