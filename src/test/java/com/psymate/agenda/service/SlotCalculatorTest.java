package com.psymate.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.AppointmentStatus;
import com.psymate.agenda.model.BookingConfirmation;
import com.psymate.agenda.service.BookingSettings.Window;
import com.psymate.agenda.service.SlotCalculator.Busy;
import com.psymate.agenda.service.SlotCalculator.Slot;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Cœur de M14-05 (G Q76) : découpe des plages, rendez-vous actifs, délai minimal, horizon, fuseau
 * et changements d'heure, sans base ni horloge.
 */
class SlotCalculatorTest {

  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
  private static final ZoneId MONTREAL = ZoneId.of("America/Montreal");

  /** Lundi 4 janvier 2027 (heure d'hiver à Paris : UTC+1). */
  private static final LocalDate MONDAY = LocalDate.of(2027, 1, 4);

  /** Une semaine avant : délai et horizon par défaut sans effet sur MONDAY. */
  private static final Instant LONG_BEFORE = Instant.parse("2026-12-28T08:00:00Z");

  private static Window window(DayOfWeek day, String start, String end) {
    return new Window(day, LocalTime.parse(start), LocalTime.parse(end));
  }

  private static BookingSettings settings(
      ZoneId zone, int durationMin, int noticeHours, int horizonDays, Window... windows) {
    return new BookingSettings(
        true,
        BookingConfirmation.manual,
        List.of(windows),
        durationMin,
        noticeHours,
        horizonDays,
        Set.of(AppointmentModality.in_person),
        zone);
  }

  private static BookingSettings paris(int durationMin, Window... windows) {
    return settings(PARIS, durationMin, 24, 28, windows);
  }

  private static List<Slot> offered(BookingSettings s, LocalDate day, Busy... busy) {
    return SlotCalculator.offered(s, day, day, LONG_BEFORE, List.of(busy));
  }

  /** Heures locales de début, dans le fuseau donné. */
  private static List<LocalDateTime> localStarts(List<Slot> slots, ZoneId zone) {
    return slots.stream().map(s -> LocalDateTime.ofInstant(s.startsAt(), zone)).toList();
  }

  private static List<LocalTime> localTimes(List<Slot> slots, ZoneId zone) {
    return localStarts(slots, zone).stream().map(LocalDateTime::toLocalTime).toList();
  }

  private static List<LocalTime> times(String... hhmm) {
    return java.util.Arrays.stream(hhmm).map(LocalTime::parse).toList();
  }

  private static Instant paris(LocalDate day, String hhmm) {
    return day.atTime(LocalTime.parse(hhmm)).atZone(PARIS).toInstant();
  }

  private static Busy busy(LocalDate day, String from, String to, AppointmentStatus status) {
    return new Busy(paris(day, from), paris(day, to), status);
  }

  private static void assertDuration(List<Slot> slots, int minutes) {
    assertThat(slots)
        .allSatisfy(
            s ->
                assertThat(Duration.between(s.startsAt(), s.endsAt()))
                    .isEqualTo(Duration.ofMinutes(minutes)));
  }

  @Nested
  class Cutting {

    @Test
    void sixtyMinuteSlotsOfAMorningWindowAreReturnedInUtc() {
      List<Slot> slots = offered(paris(60, window(DayOfWeek.MONDAY, "09:00", "12:00")), MONDAY);

      assertThat(slots)
          .containsExactly(
              new Slot(
                  Instant.parse("2027-01-04T08:00:00Z"), Instant.parse("2027-01-04T09:00:00Z")),
              new Slot(
                  Instant.parse("2027-01-04T09:00:00Z"), Instant.parse("2027-01-04T10:00:00Z")),
              new Slot(
                  Instant.parse("2027-01-04T10:00:00Z"), Instant.parse("2027-01-04T11:00:00Z")));
    }

    @Test
    void fortyFiveMinuteSlotsStartFromTheWindowStart() {
      List<Slot> slots = offered(paris(45, window(DayOfWeek.MONDAY, "09:00", "12:00")), MONDAY);

      assertThat(localTimes(slots, PARIS)).isEqualTo(times("09:00", "09:45", "10:30", "11:15"));
      assertDuration(slots, 45);
      assertThat(slots.getLast().endsAt()).isEqualTo(paris(MONDAY, "12:00"));
    }

    @Test
    void windowShorterThanOneSlotGivesNothing() {
      assertThat(offered(paris(60, window(DayOfWeek.MONDAY, "09:00", "09:30")), MONDAY)).isEmpty();
    }

    @Test
    void remainderAtTheEndOfAWindowIsIgnored() {
      List<Slot> slots = offered(paris(60, window(DayOfWeek.MONDAY, "09:00", "11:30")), MONDAY);

      assertThat(localTimes(slots, PARIS)).isEqualTo(times("09:00", "10:00"));
      assertThat(slots.getLast().endsAt()).isEqualTo(paris(MONDAY, "11:00"));
    }

    @Test
    void windowExactlyOneSlotLongGivesOneSlot() {
      assertThat(
              localTimes(
                  offered(paris(60, window(DayOfWeek.MONDAY, "09:00", "10:00")), MONDAY), PARIS))
          .isEqualTo(times("09:00"));
    }

    @Test
    void windowsOfAnotherWeekdayGiveNothingThatDay() {
      assertThat(offered(paris(60, window(DayOfWeek.TUESDAY, "09:00", "12:00")), MONDAY)).isEmpty();
    }

    @Test
    void lateEveningWindowEndingAt2355IsCut() {
      List<Slot> slots = offered(paris(15, window(DayOfWeek.MONDAY, "23:00", "23:55")), MONDAY);

      assertThat(localTimes(slots, PARIS)).isEqualTo(times("23:00", "23:15", "23:30"));
    }

    @Test
    void contiguousWindowsOfTheSameDayAreCutSeparately() {
      List<Slot> slots =
          offered(
              paris(
                  45,
                  window(DayOfWeek.MONDAY, "09:00", "10:00"),
                  window(DayOfWeek.MONDAY, "10:00", "11:00")),
              MONDAY);

      // Chaque plage part de son propre début : 09:00 puis 10:00 ; leurs restes sont ignorés.
      assertThat(localTimes(slots, PARIS)).isEqualTo(times("09:00", "10:00"));
    }

    @Test
    void everyDayOfTheRangeIsCovered() {
      BookingSettings s =
          paris(
              60,
              window(DayOfWeek.MONDAY, "09:00", "10:00"),
              window(DayOfWeek.WEDNESDAY, "14:00", "16:00"),
              window(DayOfWeek.FRIDAY, "08:00", "09:00"));

      List<Slot> slots =
          SlotCalculator.offered(s, MONDAY, MONDAY.plusDays(13), LONG_BEFORE, List.of());

      assertThat(localStarts(slots, PARIS))
          .containsExactly(
              LocalDateTime.of(2027, 1, 4, 9, 0),
              LocalDateTime.of(2027, 1, 6, 14, 0),
              LocalDateTime.of(2027, 1, 6, 15, 0),
              LocalDateTime.of(2027, 1, 8, 8, 0),
              LocalDateTime.of(2027, 1, 11, 9, 0),
              LocalDateTime.of(2027, 1, 13, 14, 0),
              LocalDateTime.of(2027, 1, 13, 15, 0),
              LocalDateTime.of(2027, 1, 15, 8, 0));
    }

    @Test
    void noWindowGivesNothing() {
      assertThat(SlotCalculator.offered(paris(60), MONDAY, MONDAY.plusDays(30), LONG_BEFORE, null))
          .isEmpty();
    }

    @Test
    void toBeforeFromGivesNothing() {
      BookingSettings s = paris(60, window(DayOfWeek.MONDAY, "09:00", "12:00"));
      assertThat(SlotCalculator.offered(s, MONDAY, MONDAY.minusDays(1), LONG_BEFORE, List.of()))
          .isEmpty();
    }
  }

  @Nested
  class Appointments {

    private final BookingSettings morning = paris(60, window(DayOfWeek.MONDAY, "09:00", "12:00"));

    @Test
    void plannedAppointmentRemovesTheSlotItOverlaps() {
      List<Slot> slots =
          offered(morning, MONDAY, busy(MONDAY, "10:00", "10:50", AppointmentStatus.planned));

      assertThat(localTimes(slots, PARIS)).isEqualTo(times("09:00", "11:00"));
    }

    @Test
    void tentativeAppointmentRemovesItToo() {
      List<Slot> slots =
          offered(morning, MONDAY, busy(MONDAY, "10:00", "10:50", AppointmentStatus.tentative));

      assertThat(localTimes(slots, PARIS)).isEqualTo(times("09:00", "11:00"));
    }

    @Test
    void cancelledAppointmentRemovesNothing() {
      List<Slot> slots =
          offered(morning, MONDAY, busy(MONDAY, "10:00", "10:50", AppointmentStatus.cancelled));

      assertThat(localTimes(slots, PARIS)).isEqualTo(times("09:00", "10:00", "11:00"));
    }

    @Test
    void doneAppointmentRemovesNothing() {
      List<Slot> slots =
          offered(morning, MONDAY, busy(MONDAY, "10:00", "10:50", AppointmentStatus.done));

      assertThat(localTimes(slots, PARIS)).isEqualTo(times("09:00", "10:00", "11:00"));
    }

    @Test
    void partialOverlapAcrossTwoSlotsRemovesBoth() {
      List<Slot> slots =
          offered(morning, MONDAY, busy(MONDAY, "09:30", "10:30", AppointmentStatus.planned));

      assertThat(localTimes(slots, PARIS)).isEqualTo(times("11:00"));
    }

    @Test
    void appointmentsTouchingASlotBoundaryDoNotRemoveIt() {
      List<Slot> slots =
          offered(
              morning,
              MONDAY,
              busy(MONDAY, "08:00", "09:00", AppointmentStatus.planned),
              busy(MONDAY, "12:00", "13:00", AppointmentStatus.tentative));

      assertThat(localTimes(slots, PARIS)).isEqualTo(times("09:00", "10:00", "11:00"));
    }

    @Test
    void shortAppointmentInsideASlotRemovesIt() {
      List<Slot> slots =
          offered(morning, MONDAY, busy(MONDAY, "11:20", "11:25", AppointmentStatus.planned));

      assertThat(localTimes(slots, PARIS)).isEqualTo(times("09:00", "10:00"));
    }

    @Test
    void longAppointmentCoveringTheWholeWindowRemovesEverything() {
      Busy allDay =
          new Busy(
              paris(MONDAY.minusDays(1), "18:00"),
              paris(MONDAY.plusDays(1), "08:00"),
              AppointmentStatus.planned);

      assertThat(offered(morning, MONDAY, allDay)).isEmpty();
    }

    @Test
    void cancelledAndActiveOnTheSameSlotStillRemoveIt() {
      List<Slot> slots =
          offered(
              morning,
              MONDAY,
              busy(MONDAY, "10:00", "11:00", AppointmentStatus.cancelled),
              busy(MONDAY, "10:00", "11:00", AppointmentStatus.tentative));

      assertThat(localTimes(slots, PARIS)).isEqualTo(times("09:00", "11:00"));
    }
  }

  @Nested
  class Notice {

    /** Dimanche 3 janvier 2027, 09:00 à Paris. */
    private final Instant now = Instant.parse("2027-01-03T08:00:00Z");

    @Test
    void slotStartingIn23HoursIsNotOfferedIn25HoursItIs() {
      // Lundi 08:00 Paris = now + 23 h ; lundi 10:00 Paris = now + 25 h.
      BookingSettings s =
          settings(
              PARIS,
              60,
              24,
              28,
              window(DayOfWeek.MONDAY, "08:00", "09:00"),
              window(DayOfWeek.MONDAY, "10:00", "11:00"));

      List<Slot> slots = SlotCalculator.offered(s, MONDAY, MONDAY, now, List.of());

      assertThat(slots).extracting(Slot::startsAt).containsExactly(now.plus(Duration.ofHours(25)));
    }

    @Test
    void slotStartingExactlyAtTheNoticeIsOffered() {
      BookingSettings s = settings(PARIS, 60, 24, 28, window(DayOfWeek.MONDAY, "09:00", "10:00"));

      List<Slot> slots = SlotCalculator.offered(s, MONDAY, MONDAY, now, List.of());

      assertThat(slots).extracting(Slot::startsAt).containsExactly(now.plus(Duration.ofHours(24)));
    }

    @Test
    void oneHourNoticeKeepsTheRestOfToday() {
      BookingSettings s = settings(PARIS, 60, 1, 28, window(DayOfWeek.SUNDAY, "08:00", "13:00"));

      List<Slot> slots =
          SlotCalculator.offered(s, LocalDate.of(2027, 1, 3), LocalDate.of(2027, 1, 3), now, null);

      // 09:00 Paris maintenant : 08:00 et 09:00 sont trop tôt, 10:00 commence dans 1 h.
      assertThat(localTimes(slots, PARIS)).isEqualTo(times("10:00", "11:00", "12:00"));
    }

    @Test
    void pastDaysGiveNothing() {
      BookingSettings s = settings(PARIS, 60, 1, 28, window(DayOfWeek.MONDAY, "09:00", "12:00"));

      assertThat(
              SlotCalculator.offered(s, MONDAY.minusWeeks(2), MONDAY.minusWeeks(1), now, List.of()))
          .isEmpty();
    }
  }

  @Nested
  class Horizon {

    private final Window[] everyDay = {
      window(DayOfWeek.MONDAY, "09:00", "10:00"),
      window(DayOfWeek.TUESDAY, "09:00", "10:00"),
      window(DayOfWeek.WEDNESDAY, "09:00", "10:00"),
      window(DayOfWeek.THURSDAY, "09:00", "10:00"),
      window(DayOfWeek.FRIDAY, "09:00", "10:00"),
      window(DayOfWeek.SATURDAY, "09:00", "10:00"),
      window(DayOfWeek.SUNDAY, "22:00", "23:55")
    };

    @Test
    void nothingBeyondTheLastDayOfTheHorizon() {
      // Lundi 4 janvier, 07:00 à Paris ; horizon 7 : dernier jour offert le lundi 11.
      Instant now = Instant.parse("2027-01-04T06:00:00Z");
      BookingSettings s = settings(PARIS, 60, 1, 7, everyDay);

      List<Slot> slots = SlotCalculator.offered(s, MONDAY, MONDAY.plusDays(20), now, List.of());

      List<LocalDate> days =
          localStarts(slots, PARIS).stream().map(LocalDateTime::toLocalDate).distinct().toList();
      assertThat(days).first().isEqualTo(MONDAY);
      assertThat(days).last().isEqualTo(MONDAY.plusDays(7));
      assertThat(days).hasSize(8);
    }

    @Test
    void lastSlotOfTheLastDayIsOfferedTheNextMorningIsNot() {
      // Samedi 2 janvier 10:00 à Paris ; horizon 8 : dernier jour offert le dimanche 10.
      Instant now = Instant.parse("2027-01-02T09:00:00Z");
      BookingSettings s = settings(PARIS, 60, 1, 8, everyDay);

      List<Slot> slots =
          SlotCalculator.offered(
              s, LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 11), now, List.of());

      assertThat(localStarts(slots, PARIS)).containsExactly(LocalDateTime.of(2027, 1, 10, 22, 0));
    }

    @Test
    void todayIsTheDateInThePractitionerTimezoneNotInUtc() {
      // 23:30 UTC le lundi 4 = 00:30 le mardi 5 à Paris : aujourd'hui est le 5, horizon 7 → le 12.
      Instant now = Instant.parse("2027-01-04T23:30:00Z");
      BookingSettings s = settings(PARIS, 60, 1, 7, everyDay);

      List<Slot> slots =
          SlotCalculator.offered(
              s, LocalDate.of(2027, 1, 12), LocalDate.of(2027, 1, 13), now, List.of());

      assertThat(localStarts(slots, PARIS)).containsExactly(LocalDateTime.of(2027, 1, 12, 9, 0));
    }

    @Test
    void windowEntirelyBeyondTheHorizonGivesNothing() {
      BookingSettings s = settings(PARIS, 60, 1, 7, everyDay);

      assertThat(
              SlotCalculator.offered(
                  s, MONDAY.plusDays(10), MONDAY.plusDays(20), LONG_BEFORE, List.of()))
          .isEmpty();
    }
  }

  @Nested
  class Timezones {

    @Test
    void montrealWindowsAreReturnedAsMontrealLocalTimesInUtc() {
      BookingSettings s =
          settings(MONTREAL, 60, 24, 28, window(DayOfWeek.MONDAY, "09:00", "12:00"));

      List<Slot> slots = SlotCalculator.offered(s, MONDAY, MONDAY, LONG_BEFORE, List.of());

      // Janvier : Montréal est à UTC-5.
      assertThat(slots)
          .extracting(Slot::startsAt)
          .containsExactly(
              Instant.parse("2027-01-04T14:00:00Z"),
              Instant.parse("2027-01-04T15:00:00Z"),
              Instant.parse("2027-01-04T16:00:00Z"));
      assertThat(localTimes(slots, MONTREAL)).isEqualTo(times("09:00", "10:00", "11:00"));
    }

    @Test
    void montrealSummerTimeIsUtcMinusFour() {
      LocalDate july = LocalDate.of(2027, 7, 5);
      BookingSettings s =
          settings(MONTREAL, 60, 24, 28, window(DayOfWeek.MONDAY, "09:00", "10:00"));

      List<Slot> slots =
          SlotCalculator.offered(s, july, july, Instant.parse("2027-06-28T12:00:00Z"), List.of());

      assertThat(slots)
          .containsExactly(
              new Slot(
                  Instant.parse("2027-07-05T13:00:00Z"), Instant.parse("2027-07-05T14:00:00Z")));
    }

    @Test
    void montrealAppointmentIsComparedByInstant() {
      BookingSettings s =
          settings(MONTREAL, 60, 24, 28, window(DayOfWeek.MONDAY, "09:00", "12:00"));
      // 15:00–16:00 UTC = 10:00–11:00 à Montréal.
      Busy b =
          new Busy(
              Instant.parse("2027-01-04T15:00:00Z"),
              Instant.parse("2027-01-04T16:00:00Z"),
              AppointmentStatus.planned);

      List<Slot> slots = SlotCalculator.offered(s, MONDAY, MONDAY, LONG_BEFORE, List.of(b));

      assertThat(localTimes(slots, MONTREAL)).isEqualTo(times("09:00", "11:00"));
    }
  }

  @Nested
  class DaylightSaving {

    /** Dernier dimanche de mars 2027 : à Paris, 02:00 passe à 03:00 (02:xx n'existe pas). */
    private final LocalDate springForward = LocalDate.of(2027, 3, 28);

    /**
     * Dernier dimanche d'octobre 2027 : à Paris, 03:00 revient à 02:00 (02:xx existe deux fois).
     */
    private final LocalDate fallBack = LocalDate.of(2027, 10, 31);

    private final Instant monthBefore = Instant.parse("2027-03-01T00:00:00Z");
    private final Instant monthBeforeFall = Instant.parse("2027-10-04T00:00:00Z");

    @Test
    void springForwardWindowGivesNoSlotAtANonexistentHour() {
      BookingSettings s = settings(PARIS, 60, 1, 90, window(DayOfWeek.SUNDAY, "02:00", "04:00"));

      List<Slot> slots =
          SlotCalculator.offered(s, springForward, springForward, monthBefore, List.of());

      assertThat(slots)
          .containsExactly(
              new Slot(
                  Instant.parse("2027-03-28T01:00:00Z"), Instant.parse("2027-03-28T02:00:00Z")));
      assertThat(localTimes(slots, PARIS)).isEqualTo(times("03:00"));
      assertThat(localTimes(slots, PARIS)).noneMatch(t -> t.getHour() == 2);
    }

    @Test
    void springForwardThirtyMinuteSlotsSkipTheMissingHour() {
      BookingSettings s = settings(PARIS, 30, 1, 90, window(DayOfWeek.SUNDAY, "01:00", "04:00"));

      List<Slot> slots =
          SlotCalculator.offered(s, springForward, springForward, monthBefore, List.of());

      // 01:30 finirait à « 02:00 », qui n'existe pas : le changement d'heure le traverse.
      assertThat(localTimes(slots, PARIS)).isEqualTo(times("01:00", "03:00", "03:30"));
      assertDuration(slots, 30);
    }

    @Test
    void springForwardSlotsNeverOverlap() {
      BookingSettings s = settings(PARIS, 45, 1, 90, window(DayOfWeek.SUNDAY, "00:00", "06:00"));

      List<Slot> slots =
          SlotCalculator.offered(s, springForward, springForward, monthBefore, List.of());

      assertDuration(slots, 45);
      for (int i = 1; i < slots.size(); i++) {
        assertThat(slots.get(i).startsAt()).isAfterOrEqualTo(slots.get(i - 1).endsAt());
      }
      assertThat(localTimes(slots, PARIS)).noneMatch(t -> t.getHour() == 2);
    }

    @Test
    void fallBackGivesNoDuplicateLocalHour() {
      BookingSettings s = settings(PARIS, 60, 1, 90, window(DayOfWeek.SUNDAY, "01:00", "04:00"));

      List<Slot> slots = SlotCalculator.offered(s, fallBack, fallBack, monthBeforeFall, List.of());

      // 01:00 (heure d'été) et 03:00 (heure d'hiver) ; 02:00 durerait deux heures réelles.
      assertThat(slots)
          .containsExactly(
              new Slot(
                  Instant.parse("2027-10-30T23:00:00Z"), Instant.parse("2027-10-31T00:00:00Z")),
              new Slot(
                  Instant.parse("2027-10-31T02:00:00Z"), Instant.parse("2027-10-31T03:00:00Z")));
      assertThat(localTimes(slots, PARIS)).doesNotHaveDuplicates();
    }

    @Test
    void fallBackRepeatedHourIsOfferedOnceAtItsFirstOccurrence() {
      BookingSettings s = settings(PARIS, 30, 1, 90, window(DayOfWeek.SUNDAY, "02:00", "03:00"));

      List<Slot> slots = SlotCalculator.offered(s, fallBack, fallBack, monthBeforeFall, List.of());

      // 02:00 heure d'été (00:00 UTC) ; 02:30 finirait à « 03:00 » une heure et demie plus tard.
      assertThat(slots)
          .containsExactly(
              new Slot(
                  Instant.parse("2027-10-31T00:00:00Z"), Instant.parse("2027-10-31T00:30:00Z")));
    }

    @Test
    void fallBackDayIsCutWithoutOverlapOrDuplicate() {
      BookingSettings s = settings(PARIS, 15, 1, 90, window(DayOfWeek.SUNDAY, "00:00", "06:00"));

      List<Slot> slots = SlotCalculator.offered(s, fallBack, fallBack, monthBeforeFall, List.of());

      assertDuration(slots, 15);
      assertThat(localTimes(slots, PARIS)).doesNotHaveDuplicates();
      for (int i = 1; i < slots.size(); i++) {
        assertThat(slots.get(i).startsAt()).isAfterOrEqualTo(slots.get(i - 1).endsAt());
      }
    }

    @Test
    void ordinaryDaysAroundTheChangeKeepTheirLocalHours() {
      BookingSettings s = settings(PARIS, 60, 1, 90, window(DayOfWeek.MONDAY, "09:00", "10:00"));

      List<Slot> before =
          SlotCalculator.offered(
              s, LocalDate.of(2027, 3, 22), LocalDate.of(2027, 3, 22), monthBefore, List.of());
      List<Slot> after =
          SlotCalculator.offered(
              s, LocalDate.of(2027, 3, 29), LocalDate.of(2027, 3, 29), monthBefore, List.of());

      assertThat(before)
          .extracting(Slot::startsAt)
          .containsExactly(Instant.parse("2027-03-22T08:00:00Z"));
      assertThat(after)
          .extracting(Slot::startsAt)
          .containsExactly(Instant.parse("2027-03-29T07:00:00Z"));
    }
  }

  @Nested
  class Ordering {

    @Test
    void resultsAreSortedByStartWhateverTheWindowOrder() {
      BookingSettings s =
          paris(
              60,
              window(DayOfWeek.TUESDAY, "14:00", "16:00"),
              window(DayOfWeek.MONDAY, "15:00", "17:00"),
              window(DayOfWeek.MONDAY, "08:00", "10:00"),
              window(DayOfWeek.TUESDAY, "09:00", "10:00"));

      List<Slot> slots =
          SlotCalculator.offered(s, MONDAY, MONDAY.plusDays(1), LONG_BEFORE, List.of());

      List<Slot> sorted = new ArrayList<>(slots);
      sorted.sort(Comparator.comparing(Slot::startsAt));
      assertThat(slots).isEqualTo(sorted).doesNotHaveDuplicates();
      assertThat(localStarts(slots, PARIS))
          .containsExactly(
              LocalDateTime.of(2027, 1, 4, 8, 0),
              LocalDateTime.of(2027, 1, 4, 9, 0),
              LocalDateTime.of(2027, 1, 4, 15, 0),
              LocalDateTime.of(2027, 1, 4, 16, 0),
              LocalDateTime.of(2027, 1, 5, 9, 0),
              LocalDateTime.of(2027, 1, 5, 14, 0),
              LocalDateTime.of(2027, 1, 5, 15, 0));
    }

    @Test
    void duplicatedWindowsDoNotDuplicateSlots() {
      BookingSettings s =
          paris(
              60,
              window(DayOfWeek.MONDAY, "09:00", "11:00"),
              window(DayOfWeek.MONDAY, "09:00", "11:00"));

      assertThat(localTimes(offered(s, MONDAY), PARIS)).isEqualTo(times("09:00", "10:00"));
    }
  }
}
