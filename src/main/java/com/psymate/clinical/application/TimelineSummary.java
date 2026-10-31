package com.psymate.clinical.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Agrégats par période (mois ou année UTC), périodes vides omises, ordre croissant. */
public record TimelineSummary(String granularity, List<Period> periods) {

  /**
   * @param period « 2026-10 » (mois) ou « 2026 » (année)
   * @param from début de période, UTC
   * @param counts nombre d'entrées par type
   * @param averageIntensity moyenne des intensités des ressentis, nulle sans ressenti
   */
  public record Period(
      String period,
      Instant from,
      Map<String, Integer> counts,
      int feelingCount,
      Double averageIntensity) {}
}
