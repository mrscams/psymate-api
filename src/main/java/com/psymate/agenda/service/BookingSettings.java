package com.psymate.agenda.service;

import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.BookingConfirmation;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Réglages de prise de rendez-vous en ligne d'un praticien (G Q75), typés : les huit clés {@code
 * booking.*} de ses préférences, lues par {@link BookingSettingsReader}. Les plages sont triées par
 * jour puis début ; les modalités suivent l'ordre {@code in_person}, {@code video}, {@code phone}.
 *
 * @param enabled {@code booking.enabled}
 * @param confirmation {@code booking.confirmation}
 * @param windows {@code booking.windows}, triées
 * @param slotDurationMin {@code booking.slotDurationMin}, durée d'un créneau en minutes
 * @param minNoticeHours {@code booking.minNoticeHours}, délai minimal avant un créneau
 * @param horizonDays {@code booking.horizonDays}, dernier jour offert à partir d'aujourd'hui
 * @param modalities {@code booking.modalities}, jamais vide
 * @param timezone {@code booking.timezone}, fuseau des plages et des dates publiques
 */
public record BookingSettings(
    boolean enabled,
    BookingConfirmation confirmation,
    List<Window> windows,
    int slotDurationMin,
    int minNoticeHours,
    int horizonDays,
    Set<AppointmentModality> modalities,
    ZoneId timezone) {

  /** Défauts de G Q75 (identiques à ceux d'identity). */
  public static final boolean DEFAULT_ENABLED = false;

  public static final BookingConfirmation DEFAULT_CONFIRMATION = BookingConfirmation.manual;
  public static final int DEFAULT_SLOT_DURATION_MIN = 60;
  public static final int DEFAULT_MIN_NOTICE_HOURS = 24;
  public static final int DEFAULT_HORIZON_DAYS = 28;
  public static final Set<AppointmentModality> DEFAULT_MODALITIES =
      Collections.unmodifiableSet(EnumSet.of(AppointmentModality.in_person));
  public static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Europe/Paris");

  private static final Comparator<Window> ORDER =
      Comparator.comparing(Window::day).thenComparing(Window::start).thenComparing(Window::end);

  public BookingSettings {
    Objects.requireNonNull(confirmation, "confirmation");
    Objects.requireNonNull(timezone, "timezone");
    windows = windows == null ? List.of() : windows.stream().sorted(ORDER).toList();
    modalities =
        modalities == null || modalities.isEmpty()
            ? DEFAULT_MODALITIES
            : Collections.unmodifiableSet(EnumSet.copyOf(modalities));
    if (slotDurationMin <= 0 || minNoticeHours < 0 || horizonDays < 0) {
      throw new IllegalArgumentException("durée, délai et horizon positifs");
    }
  }

  /** Réglages par défaut : fonction désactivée, aucune plage. */
  public static BookingSettings defaults() {
    return new BookingSettings(
        DEFAULT_ENABLED,
        DEFAULT_CONFIRMATION,
        List.of(),
        DEFAULT_SLOT_DURATION_MIN,
        DEFAULT_MIN_NOTICE_HOURS,
        DEFAULT_HORIZON_DAYS,
        DEFAULT_MODALITIES,
        DEFAULT_TIMEZONE);
  }

  /**
   * Plage hebdomadaire ouverte à la prise de rendez-vous : un jour, une heure locale de début et
   * une de fin (strictement après), dans le fuseau du praticien.
   */
  public record Window(DayOfWeek day, LocalTime start, LocalTime end) {

    public Window {
      Objects.requireNonNull(day, "day");
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
      if (!end.isAfter(start)) {
        throw new IllegalArgumentException("end doit suivre start");
      }
    }
  }
}
