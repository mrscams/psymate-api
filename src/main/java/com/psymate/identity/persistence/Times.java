package com.psymate.identity.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Conversions entre les {@link Instant} des services et les {@link OffsetDateTime} UTC des entités,
 * à la précision de PostgreSQL (microsecondes).
 */
public final class Times {

  private Times() {}

  public static OffsetDateTime utc(Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  public static Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }
}
