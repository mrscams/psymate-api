package com.psymate.seed;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Agenda de démonstration d'un praticien (M11-09, fiche 15) : générateur pur et déterministe, sans
 * HTTP. Huit semaines autour d'aujourd'hui, 4 passées et 4 à venir (la semaine courante compte
 * parmi les semaines à venir du lundi au mercredi, parmi les passées du jeudi au dimanche), 11 à 16
 * rendez-vous par semaine dont un annulé (donc 10 à 15 actifs ; la semaine courante en a 14 comme
 * la maquette), du lundi au samedi entre 8 h et 19 h à Paris, 70 % au cabinet, 20 % en visio, 10 %
 * au téléphone (sur l'ensemble des occurrences), un rendez-vous « à confirmer » par semaine et
 * trois séries hebdomadaires (4 à 6 occurrences).
 *
 * <p>Les créneaux sont des heures pleines distinctes dans la semaine : le praticien n'a aucun
 * chevauchement. Une série est écrite en un seul appel ({@code recurrence}) et l'API garde l'heure
 * locale de Paris d'une occurrence à l'autre, y compris au changement d'heure.
 */
public final class AgendaScenario {

  /** Fuseau des praticiens du seed (fuseau par défaut de l'agenda). */
  public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

  /** Semaines de la fenêtre, du lundi au dimanche. */
  public static final int WEEKS = 8;

  /** Rendez-vous de la semaine courante, annulé compris : 14 actifs comme la maquette. */
  static final int CURRENT_WEEK_TOTAL = 15;

  static final int MIN_WEEK_TOTAL = 11;
  static final int MAX_WEEK_TOTAL = 16;

  /** Heures de début (heure locale) : 8 h à 18 h en semaine, 9 h à 12 h le samedi. */
  private static final int FIRST_HOUR = 8;

  private static final int LAST_HOUR_WEEKDAY = 18;
  private static final int FIRST_HOUR_SATURDAY = 9;
  private static final int LAST_HOUR_SATURDAY = 12;
  private static final int SATURDAY = 5;

  private static final List<String> NOTES =
      List.of(
          "Apporter le compte rendu de la prise de sang",
          "Bilan à mi-parcours du suivi",
          "Prévoir dix minutes pour le questionnaire",
          "Reprise après les vacances",
          "Point sur l'organisation au travail");

  private static final List<String> CANCEL_REASONS =
      List.of(
          "Empêchement du patient, prévenu la veille",
          "Report demandé par le patient",
          "Patient malade");

  /**
   * Un rendez-vous à créer, ou une série hebdomadaire si {@code weeklyOccurrences > 1} (même heure
   * locale, même modalité, même patient).
   *
   * @param patient indice dans la liste des patients du praticien
   * @param cancelReason non nul si le rendez-vous est annulé juste après sa création
   */
  public record AppointmentPlan(
      int patient,
      Instant startsAt,
      int durationMin,
      String modality,
      boolean tentative,
      int weeklyOccurrences,
      String planningNote,
      String cancelReason) {

    public boolean cancelled() {
      return cancelReason != null;
    }

    public boolean series() {
      return weeklyOccurrences > 1;
    }
  }

  /** Une occurrence, séries dépliées (pour les contrôles). {@code series} vaut 0 hors série. */
  public record Occurrence(
      int patient,
      Instant startsAt,
      Instant endsAt,
      String modality,
      boolean tentative,
      boolean cancelled,
      int series) {}

  private record Slot(int day, int hour) {}

  private record SeriesSpec(int firstWeek, int count, String modality) {}

  /** Séries : semaine de départ (0 à 7 dans la fenêtre), occurrences, modalité. */
  private static final List<SeriesSpec> SERIES =
      List.of(
          new SeriesSpec(1, 6, "in_person"),
          new SeriesSpec(4, 4, "video"),
          new SeriesSpec(0, 4, "in_person"));

  private AgendaScenario() {}

  /** Lundi de la semaine courante. */
  public static LocalDate currentMonday(LocalDate today) {
    return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  /**
   * Rang (0 à 7) de la semaine courante dans la fenêtre : 4 du lundi au mercredi (la semaine
   * courante est surtout à venir), 3 du jeudi au dimanche (elle est surtout passée). La fenêtre
   * compte ainsi 4 semaines passées et 4 à venir, et tient dans [aujourd'hui − 5 semaines,
   * aujourd'hui + 5 semaines].
   */
  public static int currentWeekIndex(LocalDate today) {
    return today.getDayOfWeek().getValue() <= DayOfWeek.WEDNESDAY.getValue() ? 4 : 3;
  }

  /** Lundi de la première semaine de la fenêtre. */
  public static LocalDate firstMonday(LocalDate today) {
    return currentMonday(today).minusWeeks(currentWeekIndex(today));
  }

  /** Début de la fenêtre : premier lundi, 0 h à Paris. */
  public static Instant windowStart(LocalDate today) {
    return firstMonday(today).atStartOfDay(ZONE).toInstant();
  }

  /** Fin exclue de la fenêtre : 8 semaines après son début (56 jours, sous la limite de 62). */
  public static Instant windowEnd(LocalDate today) {
    return firstMonday(today).plusWeeks(WEEKS).atStartOfDay(ZONE).toInstant();
  }

  /**
   * @param patients nombre de patients actifs du praticien (au moins 1)
   * @param today jour courant à Paris
   */
  public static List<AppointmentPlan> generate(int patients, LocalDate today, long seed) {
    if (patients < 1) {
      throw new IllegalArgumentException("Au moins un patient pour poser des rendez-vous");
    }
    Random rnd = new Random(seed);
    LocalDate monday0 = firstMonday(today);
    int current = currentWeekIndex(today);
    Map<Integer, Set<Slot>> taken = new HashMap<>();
    List<AppointmentPlan> plans = new ArrayList<>();

    // 1. séries : un créneau de semaine (lundi-vendredi) libre sur toutes leurs semaines
    Map<Integer, Integer> seriesPerWeek = new HashMap<>();
    for (int s = 0; s < SERIES.size(); s++) {
      SeriesSpec spec = SERIES.get(s);
      Slot slot;
      do {
        slot = new Slot(rnd.nextInt(5), FIRST_HOUR + rnd.nextInt(LAST_HOUR_WEEKDAY - FIRST_HOUR));
      } while (!freeOverWeeks(taken, slot, spec));
      for (int w = spec.firstWeek(); w < spec.firstWeek() + spec.count(); w++) {
        taken.computeIfAbsent(w, k -> new HashSet<>()).add(slot);
        seriesPerWeek.merge(w, 1, Integer::sum);
      }
      plans.add(
          new AppointmentPlan(
              s % patients,
              at(monday0.plusWeeks(spec.firstWeek()), slot),
              "video".equals(spec.modality()) ? 45 : 50,
              spec.modality(),
              false,
              spec.count(),
              null,
              null));
    }

    // 2. rendez-vous isolés, semaine par semaine (modalité et durée posées à l'étape 3)
    List<AppointmentPlan> singles = new ArrayList<>();
    for (int w = 0; w < WEEKS; w++) {
      int total =
          w == current
              ? CURRENT_WEEK_TOTAL
              : MIN_WEEK_TOTAL + rnd.nextInt(MAX_WEEK_TOTAL - MIN_WEEK_TOTAL + 1);
      int count = total - seriesPerWeek.getOrDefault(w, 0);
      Set<Slot> used = taken.computeIfAbsent(w, k -> new HashSet<>());
      // samedi travaillé : au moins un rendez-vous le samedi
      List<Slot> saturday = freeSlots(used, true);
      Collections.shuffle(saturday, rnd);
      List<Slot> weekSlots = new ArrayList<>();
      weekSlots.add(saturday.get(0));
      used.add(saturday.get(0));
      List<Slot> others = freeSlots(used, false);
      others.addAll(freeSlots(used, true));
      Collections.shuffle(others, rnd);
      weekSlots.addAll(others.subList(0, count - 1));
      used.addAll(weekSlots);
      weekSlots.sort(Comparator.comparingInt(Slot::day).thenComparingInt(Slot::hour));

      int tentative = rnd.nextInt(count);
      int cancelled = (tentative + 1 + rnd.nextInt(count - 1)) % count;
      Map<Integer, Set<Integer>> patientsByDay = new HashMap<>();
      for (int i = 0; i < count; i++) {
        Slot slot = weekSlots.get(i);
        Set<Integer> sameDay = patientsByDay.computeIfAbsent(slot.day(), k -> new HashSet<>());
        int patient = rnd.nextInt(patients);
        for (int tries = 0; tries < patients && sameDay.contains(patient); tries++) {
          patient = (patient + 1) % patients;
        }
        sameDay.add(patient);
        singles.add(
            new AppointmentPlan(
                patient,
                at(monday0.plusWeeks(w), slot),
                0,
                null,
                i == tentative,
                1,
                rnd.nextInt(8) == 0 ? NOTES.get(rnd.nextInt(NOTES.size())) : null,
                i == cancelled ? CANCEL_REASONS.get(rnd.nextInt(CANCEL_REASONS.size())) : null));
      }
    }

    // 3. modalités : 70 / 20 / 10 sur l'ensemble des occurrences, séries comprises
    int seriesOccurrences = SERIES.stream().mapToInt(SeriesSpec::count).sum();
    int seriesVideo =
        SERIES.stream().filter(s -> "video".equals(s.modality())).mapToInt(SeriesSpec::count).sum();
    int total = seriesOccurrences + singles.size();
    int video = Math.max(0, Math.round(total * 0.2f) - seriesVideo);
    int phone = Math.round(total * 0.1f);
    List<String> deck = new ArrayList<>();
    for (int i = 0; i < singles.size(); i++) {
      deck.add(i < video ? "video" : i < video + phone ? "phone" : "in_person");
    }
    Collections.shuffle(deck, rnd);
    for (int i = 0; i < singles.size(); i++) {
      AppointmentPlan p = singles.get(i);
      String modality = deck.get(i);
      int duration =
          switch (modality) {
            case "phone" -> 30;
            case "video" -> 45;
            default -> rnd.nextInt(4) == 0 ? 60 : 50;
          };
      plans.add(
          new AppointmentPlan(
              p.patient(),
              p.startsAt(),
              duration,
              modality,
              p.tentative(),
              1,
              p.planningNote(),
              p.cancelReason()));
    }
    return plans;
  }

  /** Déplie les séries en occurrences (même heure locale de Paris chaque semaine), par début. */
  public static List<Occurrence> occurrences(List<AppointmentPlan> plans) {
    List<Occurrence> out = new ArrayList<>();
    int series = 0;
    for (AppointmentPlan p : plans) {
      int seriesIndex = p.series() ? ++series : 0;
      for (int k = 0; k < p.weeklyOccurrences(); k++) {
        Instant start = p.startsAt().atZone(ZONE).plusWeeks(k).toInstant();
        out.add(
            new Occurrence(
                p.patient(),
                start,
                start.plusSeconds(60L * p.durationMin()),
                p.modality(),
                p.tentative(),
                p.cancelled(),
                seriesIndex));
      }
    }
    out.sort(Comparator.comparing(Occurrence::startsAt));
    return out;
  }

  private static boolean freeOverWeeks(Map<Integer, Set<Slot>> taken, Slot slot, SeriesSpec spec) {
    for (int w = spec.firstWeek(); w < spec.firstWeek() + spec.count(); w++) {
      if (taken.getOrDefault(w, Set.of()).contains(slot)) {
        return false;
      }
    }
    return true;
  }

  private static List<Slot> freeSlots(Set<Slot> used, boolean saturday) {
    List<Slot> free = new ArrayList<>();
    int firstDay = saturday ? SATURDAY : 0;
    int lastDay = saturday ? SATURDAY : SATURDAY - 1;
    for (int d = firstDay; d <= lastDay; d++) {
      int first = saturday ? FIRST_HOUR_SATURDAY : FIRST_HOUR;
      int last = saturday ? LAST_HOUR_SATURDAY : LAST_HOUR_WEEKDAY;
      for (int h = first; h <= last; h++) {
        Slot s = new Slot(d, h);
        if (!used.contains(s)) {
          free.add(s);
        }
      }
    }
    return free;
  }

  private static Instant at(LocalDate monday, Slot slot) {
    return monday
        .plusDays(slot.day())
        .atTime(LocalTime.of(slot.hour(), 0))
        .atZone(ZONE)
        .toInstant();
  }
}
