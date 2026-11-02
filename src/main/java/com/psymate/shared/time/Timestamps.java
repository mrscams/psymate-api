package com.psymate.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Horodatages posés par le code Java (ARCH-18) : UTC, à la précision de PostgreSQL ({@code
 * timestamptz} garde des microsecondes). Tronquer avant l'écriture évite qu'une valeur relue
 * diffère de celle que le service a gardée en mémoire.
 */
public final class Timestamps {

  private Timestamps() {}

  /** Heure courante de l'horloge, en UTC, tronquée aux microsecondes. */
  public static OffsetDateTime now(Clock clock) {
    return OffsetDateTime.now(clock)
        .withOffsetSameInstant(ZoneOffset.UTC)
        .truncatedTo(ChronoUnit.MICROS);
  }

  /** Heure courante du système, en UTC, tronquée aux microsecondes. */
  public static OffsetDateTime now() {
    return now(Clock.systemUTC());
  }

  /** Même instant tronqué aux microsecondes, décalage conservé. */
  public static OffsetDateTime micros(OffsetDateTime value) {
    return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
  }

  /**
   * Instant d'un service vers une colonne {@code timestamptz} : UTC, microsecondes ; null gardé.
   */
  public static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  /** Colonne {@code timestamptz} vers l'instant d'un service ; null gardé. */
  public static Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }
}
