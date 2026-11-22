package com.psymate.agenda.service;

import com.psymate.agenda.model.AppointmentStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Créneaux offerts à la prise de rendez-vous en ligne (G Q76), calcul pur : ni base, ni horloge (l'
 * instant courant est un paramètre), aucun état.
 *
 * <p>Règles, dans le fuseau {@link BookingSettings#timezone()} du praticien :
 *
 * <ul>
 *   <li>chaque plage d'un jour est découpée en créneaux de {@code slotDurationMin} minutes à partir
 *       de son début ; une plage trop courte pour un créneau entier n'en donne aucun, le reste en
 *       fin de plage est ignoré ;
 *   <li>un créneau commence au plus tôt à {@code now + minNoticeHours} (borne incluse) ;
 *   <li>un créneau commence au plus tard le jour {@code aujourd'hui + horizonDays} (aujourd'hui :
 *       date locale de {@code now}), donc avant la fin de ce jour dans le fuseau ;
 *   <li>un créneau ne chevauche aucun rendez-vous actif ({@code planned}, {@code tentative}) :
 *       intervalles semi-ouverts, un rendez-vous qui finit à 10:00 laisse libre le créneau de 10:00
 *       ; les rendez-vous {@code cancelled} et {@code done} ne comptent pas ;
 *   <li>changements d'heure (java.time) : un créneau dont l'heure locale de début n'existe pas
 *       (saut de printemps) n'est pas offert ; plus largement, un créneau n'est offert que si aucun
 *       changement d'heure ne le traverse, c'est-à-dire si l'heure locale de son instant de fin est
 *       bien son heure locale de début plus {@code slotDurationMin}. Une heure locale répétée
 *       (retour à l'heure d'hiver) est prise à sa première occurrence (décalage d'été) : aucune
 *       heure locale n'est offerte deux fois.
 * </ul>
 *
 * Résultat trié par début, sans doublon.
 */
public final class SlotCalculator {

  private SlotCalculator() {}

  /** Créneau offert : début et fin, instants UTC ; rien d'autre (G Q76). */
  public record Slot(Instant startsAt, Instant endsAt) {}

  /** Intervalle occupé : un rendez-vous du praticien et son statut. */
  public record Busy(Instant startsAt, Instant endsAt, AppointmentStatus status) {

    public Busy {
      Objects.requireNonNull(startsAt, "startsAt");
      Objects.requireNonNull(endsAt, "endsAt");
      Objects.requireNonNull(status, "status");
    }

    boolean overlaps(Instant from, Instant to) {
      return status.isActive() && startsAt.isBefore(to) && endsAt.isAfter(from);
    }
  }

  /**
   * Créneaux offerts des jours locaux {@code from} à {@code to} inclus (vide si {@code to} précède
   * {@code from}), triés par début.
   *
   * @param settings réglages du praticien (plages, durée, délai, horizon, fuseau)
   * @param from premier jour, dans le fuseau du praticien
   * @param to dernier jour inclus, dans le fuseau du praticien
   * @param now instant courant
   * @param busy rendez-vous du praticien qui touchent la fenêtre, tous statuts (seuls les actifs
   *     comptent)
   */
  public static List<Slot> offered(
      BookingSettings settings, LocalDate from, LocalDate to, Instant now, Collection<Busy> busy) {
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(now, "now");
    ZoneId zone = settings.timezone();
    Duration length = Duration.ofMinutes(settings.slotDurationMin());
    Instant earliest = now.plus(Duration.ofHours(settings.minNoticeHours()));
    LocalDate lastOffered = now.atZone(zone).toLocalDate().plusDays(settings.horizonDays());
    LocalDate last = to.isAfter(lastOffered) ? lastOffered : to;
    List<Busy> occupied =
        busy == null ? List.of() : busy.stream().filter(b -> b.status().isActive()).toList();

    List<Slot> slots = new ArrayList<>();
    for (LocalDate day = from; !day.isAfter(last); day = day.plusDays(1)) {
      for (BookingSettings.Window window : settings.windows()) {
        if (window.day() != day.getDayOfWeek()) {
          continue;
        }
        LocalDateTime windowEnd = day.atTime(window.end());
        for (LocalDateTime start = day.atTime(window.start());
            !start.plus(length).isAfter(windowEnd);
            start = start.plus(length)) {
          Slot slot = resolve(start, length, zone);
          if (slot != null
              && !slot.startsAt().isBefore(earliest)
              && occupied.stream().noneMatch(b -> b.overlaps(slot.startsAt(), slot.endsAt()))) {
            slots.add(slot);
          }
        }
      }
    }
    return slots.stream().sorted(Comparator.comparing(Slot::startsAt)).distinct().toList();
  }

  /**
   * Instants d'un créneau local, ou {@code null} si un changement d'heure l'empêche : heure de
   * début inexistante, ou heure locale de fin différente de début plus durée. Heure ambiguë :
   * décalage le plus tôt, celui d'avant le changement ({@link ZonedDateTime#of}).
   */
  private static Slot resolve(LocalDateTime localStart, Duration length, ZoneId zone) {
    if (zone.getRules().getValidOffsets(localStart).isEmpty()) {
      return null;
    }
    Instant startsAt = ZonedDateTime.of(localStart, zone).toInstant();
    Instant endsAt = startsAt.plus(length);
    if (!LocalDateTime.ofInstant(endsAt, zone).equals(localStart.plus(length))) {
      return null;
    }
    return new Slot(startsAt, endsAt);
  }
}
