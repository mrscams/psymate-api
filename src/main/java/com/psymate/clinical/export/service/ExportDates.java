package com.psymate.clinical.export.service;

import com.psymate.clinical.domain.TimeSpan;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Dates d'un export, en UTC, avec la précision portée par le domaine (A4.2) : l'heure pour un
 * instant exact, le jour, le mois, l'année, ou le libellé d'origine d'une période ou d'une phase de
 * vie.
 */
final class ExportDates {

  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
  private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");

  private ExportDates() {}

  static String day(LocalDate date) {
    return DAY.format(date);
  }

  static String dayTime(Instant instant) {
    return DAY_TIME.format(instant.atOffset(ZoneOffset.UTC));
  }

  /** Date d'occurrence affichée selon la précision (jamais plus précise que ce que l'on sait). */
  static String label(TimeSpan span, ExportTexts texts) {
    Locale locale = texts.locale();
    var from = span.from().atOffset(ZoneOffset.UTC);
    return switch (span.precision()) {
      case EXACT -> DAY_TIME.format(from);
      case DAY -> DAY.format(from);
      case MONTH -> DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(from);
      case YEAR -> YEAR.format(from);
      case PERIOD -> {
        String name = span.label() == null ? "" : span.label();
        String range =
            span.to() == null
                ? texts.get("period.since", DAY.format(from))
                : texts.get(
                    "period.range",
                    DAY.format(from),
                    DAY.format(span.to().atOffset(ZoneOffset.UTC)));
        yield name.isBlank() ? range : name + " (" + range + ")";
      }
      case LIFE_PHASE -> span.label() == null ? "" : span.label();
    };
  }
}
