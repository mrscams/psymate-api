package com.psymate.agenda.service;

import com.psymate.agenda.model.AppointmentException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Occurrences d'une série (G Q55) : {@code startsAt + k × everyWeeks} semaines, calculées en heure
 * locale dans le fuseau du praticien. Un changement d'heure ne décale donc pas l'heure affichée :
 * 10:00 à Paris reste 10:00 après le dernier dimanche d'octobre (l'instant UTC, lui, change). Une
 * heure locale qui n'existe pas (saut de printemps) est avancée de la durée du saut, comme le fait
 * {@link ZonedDateTime#plusWeeks}. Aucun décalage sur jour férié. Rien de la règle n'est stocké.
 */
public final class RecurrenceCalculator {

  /** Fuseau par défaut du calcul (I3, comme freebusy). */
  public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Paris");

  /** Intervalles admis, en semaines (fiche 15b « Répéter »). */
  public static final Set<Integer> EVERY_WEEKS = Set.of(1, 2, 4);

  private RecurrenceCalculator() {}

  /**
   * Débuts des occurrences, premier rendez-vous compris, dans l'ordre. Exactement un de {@code
   * count} (au moins 1) et {@code until} (jour local inclus, pas avant le premier) ; plus de {@link
   * AppointmentException#MAX_OCCURRENCES} occurrences lève {@code TOO_MANY_OCCURRENCES} sans
   * calculer plus loin.
   *
   * @throws IllegalArgumentException si les paramètres n'ont pas été validés en amont
   */
  public static List<Instant> occurrences(
      Instant startsAt, ZoneId zone, int everyWeeks, Integer count, LocalDate until) {
    if (!EVERY_WEEKS.contains(everyWeeks)) {
      throw new IllegalArgumentException("everyWeeks : 1, 2 ou 4");
    }
    if ((count == null) == (until == null)) {
      throw new IllegalArgumentException("exactement un de count et until");
    }
    if (count != null && count < 1) {
      throw new IllegalArgumentException("count >= 1");
    }
    ZonedDateTime first = startsAt.atZone(zone);
    if (until != null && until.isBefore(first.toLocalDate())) {
      throw new IllegalArgumentException("until avant le premier rendez-vous");
    }
    if (count != null && count > AppointmentException.MAX_OCCURRENCES) {
      throw AppointmentException.tooManyOccurrences();
    }
    List<Instant> starts = new ArrayList<>();
    for (int k = 0; ; k++) {
      ZonedDateTime next = first.plusWeeks((long) k * everyWeeks);
      boolean more = count != null ? k < count : !next.toLocalDate().isAfter(until);
      if (!more) {
        return starts;
      }
      if (starts.size() == AppointmentException.MAX_OCCURRENCES) {
        throw AppointmentException.tooManyOccurrences();
      }
      starts.add(next.toInstant());
    }
  }
}
