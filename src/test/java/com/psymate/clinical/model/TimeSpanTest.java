package com.psymate.clinical.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.clinical.model.TimeSpan.Precision;
import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Table de cas des six précisions de A4.2. */
class TimeSpanTest {

  private static final LocalDate BIRTH = LocalDate.of(1990, 4, 2);

  private static TimeSpec at(Precision p, String instant) {
    return new TimeSpec(p, Instant.parse(instant), null, null, false);
  }

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of(
            "exact",
            TimeSpec.exact(Instant.parse("2026-03-12T14:30:00Z")),
            "2026-03-12T14:30:00Z",
            "2026-03-12T14:30:00Z",
            Precision.EXACT),
        Arguments.of(
            "day",
            at(Precision.DAY, "2026-03-12T14:30:00Z"),
            "2026-03-12T00:00:00Z",
            "2026-03-12T23:59:59Z",
            Precision.DAY),
        Arguments.of(
            "month",
            at(Precision.MONTH, "2026-03-12T14:30:00Z"),
            "2026-03-01T00:00:00Z",
            "2026-03-31T23:59:59Z",
            Precision.MONTH),
        Arguments.of(
            "month bissextile",
            at(Precision.MONTH, "2024-02-10T00:00:00Z"),
            "2024-02-01T00:00:00Z",
            "2024-02-29T23:59:59Z",
            Precision.MONTH),
        Arguments.of(
            "year",
            at(Precision.YEAR, "2019-07-01T00:00:00Z"),
            "2019-01-01T00:00:00Z",
            "2019-12-31T23:59:59Z",
            Precision.YEAR),
        Arguments.of(
            "period",
            new TimeSpec(
                Precision.PERIOD,
                Instant.parse("2019-06-21T00:00:00Z"),
                Instant.parse("2019-09-22T00:00:00Z"),
                "été 2019",
                false),
            "2019-06-21T00:00:00Z",
            "2019-09-22T00:00:00Z",
            Precision.PERIOD),
        Arguments.of(
            "life_phase enfance",
            new TimeSpec(Precision.LIFE_PHASE, null, null, "enfance", false),
            "1990-04-02T00:00:00Z",
            "2002-04-02T00:00:00Z",
            Precision.LIFE_PHASE),
        Arguments.of(
            "ongoing depuis mars 2026",
            TimeSpec.ongoing(Precision.MONTH, Instant.parse("2026-03-15T00:00:00Z")),
            "2026-03-01T00:00:00Z",
            null,
            Precision.MONTH));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void boundsMatchA42(String name, TimeSpec spec, String from, String to, Precision precision) {
    TimeSpan span = TimeSpan.resolve(spec, BIRTH);
    assertThat(span.from()).isEqualTo(Instant.parse(from));
    assertThat(span.to()).isEqualTo(to == null ? null : Instant.parse(to));
    assertThat(span.precision()).isEqualTo(precision);
  }

  @Test
  void periodAndLifePhaseKeepTheirLabel() {
    TimeSpan period =
        TimeSpan.resolve(
            new TimeSpec(
                Precision.PERIOD,
                Instant.parse("2019-06-21T00:00:00Z"),
                Instant.parse("2019-09-22T00:00:00Z"),
                "été 2019",
                false),
            BIRTH);
    assertThat(period.label()).isEqualTo("été 2019");
    TimeSpan phase =
        TimeSpan.resolve(new TimeSpec(Precision.LIFE_PHASE, null, null, "Enfance", false), BIRTH);
    assertThat(phase.label()).isEqualTo("Enfance");
  }

  @Test
  void periodWithoutLabelOrEndIsRefused() {
    Instant a = Instant.parse("2019-06-21T00:00:00Z");
    assertThatThrownBy(
            () ->
                TimeSpan.resolve(
                    new TimeSpec(Precision.PERIOD, a, a.plusSeconds(1), " ", false), BIRTH))
        .isInstanceOf(ClinicalException.class);
    assertThatThrownBy(
            () -> TimeSpan.resolve(new TimeSpec(Precision.PERIOD, a, null, "été", false), BIRTH))
        .isInstanceOf(ClinicalException.class);
  }

  @Test
  void lifePhaseNeedsBirthDateAndKnownLabel() {
    TimeSpec enfance = new TimeSpec(Precision.LIFE_PHASE, null, null, "enfance", false);
    assertThatThrownBy(() -> TimeSpan.resolve(enfance, null)).isInstanceOf(ClinicalException.class);
    assertThatThrownBy(
            () ->
                TimeSpan.resolve(
                    new TimeSpec(Precision.LIFE_PHASE, null, null, "inconnue", false), BIRTH))
        .isInstanceOf(ClinicalException.class);
  }

  @Test
  void endBeforeStartIsRefused() {
    Instant a = Instant.parse("2026-03-12T14:30:00Z");
    assertThatThrownBy(
            () ->
                TimeSpan.resolve(
                    new TimeSpec(Precision.EXACT, a, a.minusSeconds(1), null, false), BIRTH))
        .isInstanceOf(ClinicalException.class);
  }
}
