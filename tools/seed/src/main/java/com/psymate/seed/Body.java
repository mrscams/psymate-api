package com.psymate.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Petit constructeur de corps JSON pour l'API (camelCase). Les valeurs nulles sont ignorées, les
 * instants sont écrits en ISO 8601 UTC à la seconde.
 */
final class Body {

  private final Map<String, Object> map = new LinkedHashMap<>();

  static Body of() {
    return new Body();
  }

  Body title(String title) {
    return put("title", title);
  }

  Body body(String body) {
    return put("body", body);
  }

  Body put(String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
    return this;
  }

  Body tags(List<String> tags) {
    if (tags != null && !tags.isEmpty()) {
      map.put("tags", List.copyOf(tags));
    }
    return this;
  }

  Body privateEntry() {
    map.put("visibility", "private");
    return this;
  }

  /** Date exacte (précision exact, sans fin). */
  Body at(Instant from) {
    return occurred(from, null, "exact", null);
  }

  Body occurred(Instant from, Instant to, String precision, String label) {
    Map<String, Object> o = new LinkedHashMap<>();
    if (from != null) {
      o.put("from", iso(from));
    }
    if (to != null) {
      o.put("to", iso(to));
    }
    o.put("precision", precision);
    if (label != null) {
      o.put("label", label);
    }
    map.put("occurred", o);
    return this;
  }

  /** Jour entier : bornes calculées par l'API ; {@code from} est calé au début du jour. */
  Body day(LocalDate day) {
    return occurred(day.atStartOfDay(ZoneOffset.UTC).toInstant(), null, "day", null);
  }

  Body month(LocalDate anyDayOfMonth) {
    return occurred(
        anyDayOfMonth.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
        null,
        "month",
        null);
  }

  Body year(int year) {
    return occurred(
        LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), null, "year", null);
  }

  Body period(Instant from, Instant to, String label) {
    return occurred(from, to, "period", label);
  }

  /** Phase de vie « enfance » : bornée par la date de naissance, sans {@code from}. */
  Body lifePhase(String label) {
    return occurred(null, null, "life_phase", label);
  }

  Body feeling(String emotionCode, int intensity) {
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("emotionCode", emotionCode);
    f.put("intensity", intensity);
    map.put("feeling", f);
    return this;
  }

  Body medication(
      String molecule, String dosage, String frequency, String prescriber, LocalDate periodFrom) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("molecule", molecule);
    if (dosage != null) {
      m.put("dosage", dosage);
    }
    if (frequency != null) {
      m.put("frequency", frequency);
    }
    if (prescriber != null) {
      m.put("prescriber", prescriber);
    }
    if (periodFrom != null) {
      m.put("periodFrom", periodFrom.toString());
    }
    map.put("medication", m);
    return this;
  }

  /** Correction d'un médicament : seule la fin de période est fournie (fusion champ par champ). */
  Body medicationEnds(LocalDate periodTo) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("periodTo", periodTo.toString());
    map.put("medication", m);
    return this;
  }

  Body reason(String reason) {
    return put("reason", reason);
  }

  Map<String, Object> build() {
    return map;
  }

  static String iso(Instant instant) {
    return instant.truncatedTo(ChronoUnit.SECONDS).toString();
  }
}
