package com.psymate.vocabulary;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Horodatage UTC à la précision de PostgreSQL (microsecondes), posé par le code Java. */
final class Timestamps {

  private Timestamps() {}

  static OffsetDateTime now() {
    return micros(OffsetDateTime.now(ZoneOffset.UTC));
  }

  static OffsetDateTime micros(OffsetDateTime t) {
    return t.truncatedTo(ChronoUnit.MICROS);
  }
}
