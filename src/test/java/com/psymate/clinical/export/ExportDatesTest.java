package com.psymate.clinical.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.clinical.domain.TimeSpan;
import com.psymate.clinical.domain.TimeSpan.Precision;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Précision de la date affichée dans l'export (A4.2) : jamais plus précise que le domaine. */
class ExportDatesTest {

  private static final ExportTexts FR = ExportTexts.of(Locale.FRENCH);
  private static final ExportTexts EN = ExportTexts.of(Locale.ENGLISH);

  private static TimeSpan span(Precision p, String from, String to, String label) {
    return new TimeSpan(Instant.parse(from), to == null ? null : Instant.parse(to), p, label);
  }

  @Test
  void exactShowsTheTimeInUtc() {
    assertThat(ExportDates.label(span(Precision.EXACT, "2026-10-20T19:05:00Z", null, null), FR))
        .isEqualTo("20/10/2026 19:05");
  }

  @Test
  void dayMonthAndYearDropWhatIsNotKnown() {
    assertThat(ExportDates.label(span(Precision.DAY, "2019-05-04T00:00:00Z", null, null), FR))
        .isEqualTo("04/05/2019");
    assertThat(ExportDates.label(span(Precision.MONTH, "2026-03-01T00:00:00Z", null, null), FR))
        .isEqualTo("mars 2026");
    assertThat(ExportDates.label(span(Precision.MONTH, "2026-03-01T00:00:00Z", null, null), EN))
        .isEqualTo("March 2026");
    assertThat(ExportDates.label(span(Precision.YEAR, "2019-01-01T00:00:00Z", null, null), FR))
        .isEqualTo("2019");
  }

  @Test
  void periodKeepsItsLabelAndShowsTheRangeOrOngoing() {
    assertThat(
            ExportDates.label(
                span(Precision.PERIOD, "2019-06-21T00:00:00Z", "2019-09-22T00:00:00Z", "été 2019"),
                FR))
        .isEqualTo("été 2019 (du 21/06/2019 au 22/09/2019)");
    assertThat(
            ExportDates.label(
                span(Precision.PERIOD, "2026-03-15T10:00:00Z", null, "depuis mars"), FR))
        .isEqualTo("depuis mars (depuis le 15/03/2026)");
  }

  @Test
  void lifePhaseShowsOnlyItsLabel() {
    assertThat(
            ExportDates.label(
                span(
                    Precision.LIFE_PHASE,
                    "1990-04-02T00:00:00Z",
                    "2000-04-02T00:00:00Z",
                    "enfance"),
                FR))
        .isEqualTo("enfance");
  }

  @Test
  void slugStripsAccentsAndPunctuation() {
    assertThat(PatientExportService.slug("Élodie Dupont-Ñoël")).isEqualTo("elodie-dupont-noel");
    assertThat(PatientExportService.slug("  ")).isEqualTo("patient");
    assertThat(PatientExportService.slug("a".repeat(100))).hasSize(40);
    assertThat(PatientExportService.slug("Мария")).isEqualTo("patient");
  }

  @Test
  void formatParsingIsStrict() {
    assertThat(ExportFormat.parse("md")).contains(ExportFormat.MD);
    assertThat(ExportFormat.parse(" PDF ")).contains(ExportFormat.PDF);
    assertThat(ExportFormat.parse("docx")).isEmpty();
    assertThat(ExportFormat.parse(null)).isEmpty();
  }
}
