package com.psymate.directory;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Horodatage des écritures du module, à la précision de PostgreSQL. */
final class DirectoryClock {

  private DirectoryClock() {}

  /** Heure courante de l'horloge, en UTC, tronquée aux microsecondes (précision de timestamptz). */
  static OffsetDateTime now(Clock clock) {
    return OffsetDateTime.now(clock)
        .withOffsetSameInstant(ZoneOffset.UTC)
        .truncatedTo(ChronoUnit.MICROS);
  }
}
