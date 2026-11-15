package com.psymate.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.agenda.model.AppointmentException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Occurrences d'une série (M11-07, G Q55) : bornes de count, until inclusif, 26 occurrences
 * exactement acceptées, heure locale constante à travers un changement d'heure.
 */
class RecurrenceCalculatorTest {

  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

  /** Lundi 16 novembre 2026, 10:00 à Paris (heure d'hiver, UTC+1). */
  private static final Instant MONDAY_TEN = Instant.parse("2026-11-16T09:00:00Z");

  private static List<Instant> byCount(Instant start, int everyWeeks, int count) {
    return RecurrenceCalculator.occurrences(start, PARIS, everyWeeks, count, null);
  }

  private static List<Instant> byUntil(Instant start, int everyWeeks, LocalDate until) {
    return RecurrenceCalculator.occurrences(start, PARIS, everyWeeks, null, until);
  }

  @Test
  void countFourEveryWeekGivesFourWeeklyOccurrencesStartingWithTheFirst() {
    assertThat(byCount(MONDAY_TEN, 1, 4))
        .containsExactly(
            Instant.parse("2026-11-16T09:00:00Z"),
            Instant.parse("2026-11-23T09:00:00Z"),
            Instant.parse("2026-11-30T09:00:00Z"),
            Instant.parse("2026-12-07T09:00:00Z"));
  }

  @Test
  void countOneIsTheFirstAppointmentAlone() {
    assertThat(byCount(MONDAY_TEN, 4, 1)).containsExactly(MONDAY_TEN);
  }

  @Test
  void everyFourWeeksStepsByTwentyEightDays() {
    assertThat(byCount(MONDAY_TEN, 4, 3))
        .containsExactly(
            MONDAY_TEN,
            Instant.parse("2026-12-14T09:00:00Z"),
            Instant.parse("2027-01-11T09:00:00Z"));
  }

  @Test
  void untilIsInclusive() {
    // startsAt + 7 semaines, toutes les 2 semaines : semaines 0, 2, 4, 6.
    assertThat(byUntil(MONDAY_TEN, 2, LocalDate.parse("2027-01-04"))).hasSize(4);
    // Le jour même de la quatrième occurrence l'inclut ; la veille l'exclut.
    assertThat(byUntil(MONDAY_TEN, 2, LocalDate.parse("2026-12-28")))
        .hasSize(4)
        .last()
        .isEqualTo(Instant.parse("2026-12-28T09:00:00Z"));
    assertThat(byUntil(MONDAY_TEN, 2, LocalDate.parse("2026-12-27"))).hasSize(3);
  }

  @Test
  void untilOnTheFirstDayGivesOneOccurrence() {
    assertThat(byUntil(MONDAY_TEN, 1, LocalDate.parse("2026-11-16"))).containsExactly(MONDAY_TEN);
  }

  @Test
  void untilIsReadInTheZone() {
    // Lundi 23 novembre 00:30 à Paris = dimanche 22 novembre 23:30 UTC : jour local du 23.
    Instant lateSunday = Instant.parse("2026-11-22T23:30:00Z");
    assertThat(byUntil(lateSunday, 1, LocalDate.parse("2026-11-23"))).hasSize(1);
    assertThatThrownBy(() -> byUntil(lateSunday, 1, LocalDate.parse("2026-11-22")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void exactlyTwentySixOccurrencesAreAccepted() {
    assertThat(byCount(MONDAY_TEN, 1, 26)).hasSize(26);
    assertThat(byUntil(MONDAY_TEN, 1, LocalDate.parse("2026-11-16").plusWeeks(25))).hasSize(26);
    assertThat(byUntil(MONDAY_TEN, 4, LocalDate.parse("2026-11-16").plusWeeks(100))).hasSize(26);
  }

  @Test
  void twentySevenOccurrencesAreTooMany() {
    assertThatThrownBy(() -> byCount(MONDAY_TEN, 1, 27))
        .isInstanceOf(AppointmentException.class)
        .satisfies(
            e -> assertThat(((AppointmentException) e).code()).isEqualTo("TOO_MANY_OCCURRENCES"))
        .satisfies(e -> assertThat(((AppointmentException) e).field()).isEqualTo("recurrence"));
    assertThatThrownBy(() -> byUntil(MONDAY_TEN, 1, LocalDate.parse("2026-11-16").plusWeeks(26)))
        .isInstanceOf(AppointmentException.class)
        .satisfies(
            e -> assertThat(((AppointmentException) e).code()).isEqualTo("TOO_MANY_OCCURRENCES"));
    // Une date très lointaine ne fait pas boucler le calcul.
    assertThatThrownBy(() -> byUntil(MONDAY_TEN, 1, LocalDate.parse("2999-12-31")))
        .isInstanceOf(AppointmentException.class);
  }

  @Test
  void autumnClockChangeKeepsTheLocalTime() {
    // Lundi 19 octobre 2026, 10:00 à Paris (heure d'été, UTC+2) ; passage à l'heure d'hiver le
    // dimanche 25 octobre.
    List<Instant> starts = byCount(Instant.parse("2026-10-19T08:00:00Z"), 1, 3);
    assertThat(starts)
        .containsExactly(
            Instant.parse("2026-10-19T08:00:00Z"),
            Instant.parse("2026-10-26T09:00:00Z"),
            Instant.parse("2026-11-02T09:00:00Z"));
    assertThat(starts)
        .allSatisfy(s -> assertThat(s.atZone(PARIS).toLocalTime()).isEqualTo(LocalTime.of(10, 0)));
  }

  @Test
  void springClockChangeKeepsTheLocalTime() {
    // Lundi 22 mars 2027, 10:00 à Paris (UTC+1) ; passage à l'heure d'été le dimanche 28 mars.
    List<Instant> starts = byCount(Instant.parse("2027-03-22T09:00:00Z"), 1, 2);
    assertThat(starts)
        .containsExactly(
            Instant.parse("2027-03-22T09:00:00Z"), Instant.parse("2027-03-29T08:00:00Z"));
  }

  @Test
  void aLocalTimeSkippedByTheClockChangeMovesForwardByTheGap() {
    // Dimanche 21 mars 2027, 02:30 à Paris ; le 28 mars, 02:30 n'existe pas : 03:30.
    List<Instant> starts = byCount(Instant.parse("2027-03-21T01:30:00Z"), 1, 2);
    assertThat(starts.get(1).atZone(PARIS).toLocalTime()).isEqualTo(LocalTime.of(3, 30));
  }

  @Test
  void inputsNotValidatedUpstreamAreRefused() {
    assertThatThrownBy(() -> byCount(MONDAY_TEN, 3, 4))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> byCount(MONDAY_TEN, 1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                RecurrenceCalculator.occurrences(
                    MONDAY_TEN, PARIS, 1, 4, LocalDate.parse("2026-12-31")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RecurrenceCalculator.occurrences(MONDAY_TEN, PARIS, 1, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
