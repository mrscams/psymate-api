package com.psymate.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.psymate.seed.AgendaScenario.AppointmentPlan;
import com.psymate.seed.AgendaScenario.Occurrence;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Le générateur d'agenda est pur : ces tests vérifient sans réseau les critères de M11-09 qui
 * dépendent du plan (8 semaines, 10 à 16 par semaine, lundi-samedi 8 h-19 h à Paris, 70/20/10, une
 * tentative et une annulation par semaine, séries, aucun chevauchement), sur plusieurs dates dont
 * une fenêtre qui traverse le passage à l'heure d'hiver et plusieurs nombres de patients.
 */
class AgendaScenarioTest {

  @ParameterizedTest
  @CsvSource({
    "2026-11-15, 2, 20261102",
    "2026-11-16, 3, 20261103",
    "2026-11-01, 1, 1",
    "2027-03-31, 40, 7",
    "2026-11-21, 65, 20261101",
    "2026-10-29, 4, 3"
  })
  void weeksMatchTheDemoAgenda(LocalDate today, int patients, long seed) {
    List<AppointmentPlan> plans = AgendaScenario.generate(patients, today, seed);
    List<Occurrence> all = AgendaScenario.occurrences(plans);
    LocalDate monday0 = AgendaScenario.currentMonday(today);
    Instant now = today.atTime(12, 0).atZone(AgendaScenario.ZONE).toInstant();

    Map<Long, List<Occurrence>> byWeek = new TreeMap<>();
    for (Occurrence o : all) {
      ZonedDateTime local = o.startsAt().atZone(AgendaScenario.ZONE);
      ZonedDateTime end = o.endsAt().atZone(AgendaScenario.ZONE);
      assertTrue(local.getDayOfWeek() != DayOfWeek.SUNDAY, "dimanche : " + local);
      assertTrue(!local.toLocalTime().isBefore(LocalTime.of(8, 0)), "avant 8 h : " + local);
      assertTrue(!end.toLocalTime().isAfter(LocalTime.of(19, 0)), "après 19 h : " + end);
      assertEquals(local.toLocalDate(), end.toLocalDate());
      assertTrue(o.patient() >= 0 && o.patient() < patients);
      // [now - 5 semaines, now + 5 semaines]
      assertTrue(o.startsAt().isAfter(now.minus(Duration.ofDays(35))), "trop tôt : " + local);
      assertTrue(o.endsAt().isBefore(now.plus(Duration.ofDays(35))), "trop tard : " + local);
      long week = ChronoUnit.WEEKS.between(monday0, local.toLocalDate().with(DayOfWeek.MONDAY));
      byWeek.computeIfAbsent(week, k -> new java.util.ArrayList<>()).add(o);
    }
    // 8 semaines consécutives : 4 passées et 4 à venir (la courante selon le jour)
    long first = today.getDayOfWeek().getValue() <= 3 ? -4 : -3;
    assertEquals(
        java.util.stream.LongStream.range(first, first + 8).boxed().toList(),
        List.copyOf(byWeek.keySet()));
    long upcoming = all.stream().filter(o -> o.startsAt().isAfter(now)).count();
    assertTrue(upcoming > 3 * 10, "au moins 3 semaines pleines à venir : " + upcoming);
    for (Map.Entry<Long, List<Occurrence>> week : byWeek.entrySet()) {
      List<Occurrence> os = week.getValue();
      long active = os.stream().filter(o -> !o.cancelled()).count();
      assertTrue(
          os.size() >= 10 && os.size() <= 16, "semaine " + week.getKey() + " : " + os.size());
      assertTrue(active >= 10 && active <= 16, "semaine " + week.getKey() + " actifs : " + active);
      assertEquals(1, os.stream().filter(Occurrence::tentative).count(), "tentative " + week);
      assertEquals(1, os.stream().filter(Occurrence::cancelled).count(), "annulé " + week);
      assertTrue(
          os.stream()
              .anyMatch(
                  o ->
                      o.startsAt().atZone(AgendaScenario.ZONE).getDayOfWeek()
                          == DayOfWeek.SATURDAY),
          "samedi travaillé " + week.getKey());
      assertFalse(
          os.stream().anyMatch(o -> o.tentative() && o.cancelled()),
          "tentative et annulé distincts");
      for (int i = 1; i < os.size(); i++) {
        assertFalse(
            os.get(i).startsAt().isBefore(os.get(i - 1).endsAt()),
            "chevauchement " + os.get(i - 1) + " / " + os.get(i));
      }
    }
    assertEquals(
        14, byWeek.get(0L).stream().filter(o -> !o.cancelled()).count(), "maquette : 14 séances");

    Map<String, Long> modalities =
        all.stream().collect(Collectors.groupingBy(Occurrence::modality, Collectors.counting()));
    double n = all.size();
    assertEquals(0.7, modalities.getOrDefault("in_person", 0L) / n, 0.03, modalities.toString());
    assertEquals(0.2, modalities.getOrDefault("video", 0L) / n, 0.03, modalities.toString());
    assertEquals(0.1, modalities.getOrDefault("phone", 0L) / n, 0.03, modalities.toString());

    List<AppointmentPlan> series = plans.stream().filter(AppointmentPlan::series).toList();
    assertTrue(series.size() >= 2, "quelques séries");
    assertTrue(series.stream().anyMatch(s -> s.weeklyOccurrences() >= 4), "une série de 4");
    assertTrue(series.stream().noneMatch(s -> s.tentative() || s.cancelled()));
  }

  @ParameterizedTest
  @CsvSource({"2026-11-15, 5", "2026-11-16, 105"})
  void sameSeedSameAgenda(LocalDate today, int patients) {
    assertEquals(
        AgendaScenario.generate(patients, today, 42), AgendaScenario.generate(patients, today, 42));
  }

  @ParameterizedTest
  @CsvSource({"2026-11-15", "2026-11-16", "2026-10-26"})
  void windowIsEightWeeksFromAMondayInParis(LocalDate today) {
    Instant from = AgendaScenario.windowStart(today);
    Instant to = AgendaScenario.windowEnd(today);
    ZonedDateTime start = from.atZone(AgendaScenario.ZONE);
    LocalDate monday = AgendaScenario.currentMonday(today);
    long weeksBefore = ChronoUnit.WEEKS.between(start.toLocalDate(), monday);
    assertEquals(today.getDayOfWeek().getValue() <= 3 ? 4 : 3, weeksBefore);
    assertEquals(DayOfWeek.MONDAY, start.getDayOfWeek());
    assertEquals(LocalTime.MIDNIGHT, start.toLocalTime());
    assertEquals(56, ChronoUnit.DAYS.between(start, to.atZone(AgendaScenario.ZONE)));
  }
}
