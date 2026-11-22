package com.psymate.agenda.service;

import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.BookingConfirmation;
import com.psymate.identity.api.PractitionerFacade;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Lit les huit clés {@code booking.*} d'un praticien (G Q75) par {@link
 * PractitionerFacade#preferencesOf} et les rend typées. identity les valide à l'écriture et
 * complète déjà les défauts ; par prudence, une clé absente, illisible ou hors des bornes de Q75
 * prend ici sa valeur par défaut (une liste de plages dont un élément est illisible vaut la liste
 * vide : aucune plage, donc rien d'offert).
 */
@Component
public class BookingSettingsReader {

  static final String ENABLED = "booking.enabled";
  static final String CONFIRMATION = "booking.confirmation";
  static final String WINDOWS = "booking.windows";
  static final String SLOT_DURATION_MIN = "booking.slotDurationMin";
  static final String MIN_NOTICE_HOURS = "booking.minNoticeHours";
  static final String HORIZON_DAYS = "booking.horizonDays";
  static final String MODALITIES = "booking.modalities";
  static final String TIMEZONE = "booking.timezone";

  /** HH:mm strict, comme identity. */
  private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

  private final PractitionerFacade practitioners;

  public BookingSettingsReader(PractitionerFacade practitioners) {
    this.practitioners = practitioners;
  }

  /** Réglages du praticien ; les défauts de Q75 s'il est inconnu. */
  public BookingSettings read(UUID practitionerId) {
    return of(practitioners.preferencesOf(practitionerId));
  }

  /** Réglages typés d'une table de préférences plates (ADR-11), défauts compris. */
  static BookingSettings of(Map<String, Object> preferences) {
    Map<String, Object> p = preferences == null ? Map.of() : preferences;
    return new BookingSettings(
        p.get(ENABLED) instanceof Boolean b ? b : BookingSettings.DEFAULT_ENABLED,
        confirmation(p.get(CONFIRMATION)),
        windows(p.get(WINDOWS)),
        integer(p.get(SLOT_DURATION_MIN), 15, 180, 5, BookingSettings.DEFAULT_SLOT_DURATION_MIN),
        integer(p.get(MIN_NOTICE_HOURS), 1, 168, 1, BookingSettings.DEFAULT_MIN_NOTICE_HOURS),
        integer(p.get(HORIZON_DAYS), 7, 90, 1, BookingSettings.DEFAULT_HORIZON_DAYS),
        modalities(p.get(MODALITIES)),
        timezone(p.get(TIMEZONE)));
  }

  private static BookingConfirmation confirmation(Object value) {
    for (BookingConfirmation c : BookingConfirmation.values()) {
      if (c.name().equals(value)) {
        return c;
      }
    }
    return BookingSettings.DEFAULT_CONFIRMATION;
  }

  /** Entier dans [min, max], multiple de {@code step}, sinon {@code fallback}. */
  private static int integer(Object value, int min, int max, int step, int fallback) {
    if (!(value instanceof Integer || value instanceof Long || value instanceof Short)) {
      return fallback;
    }
    long v = ((Number) value).longValue();
    return v >= min && v <= max && v % step == 0 ? (int) v : fallback;
  }

  private static Set<AppointmentModality> modalities(Object value) {
    if (!(value instanceof List<?> list) || list.isEmpty()) {
      return BookingSettings.DEFAULT_MODALITIES;
    }
    Set<AppointmentModality> modalities = EnumSet.noneOf(AppointmentModality.class);
    for (Object o : list) {
      AppointmentModality m = modality(o);
      if (m == null) {
        return BookingSettings.DEFAULT_MODALITIES;
      }
      modalities.add(m);
    }
    return modalities;
  }

  private static AppointmentModality modality(Object value) {
    for (AppointmentModality m : AppointmentModality.values()) {
      if (m.name().equals(value)) {
        return m;
      }
    }
    return null;
  }

  private static ZoneId timezone(Object value) {
    if (value instanceof String s && !s.isBlank()) {
      try {
        return ZoneId.of(s);
      } catch (DateTimeException e) {
        // Fuseau inconnu : défaut.
      }
    }
    return BookingSettings.DEFAULT_TIMEZONE;
  }

  private static List<BookingSettings.Window> windows(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<BookingSettings.Window> windows = new ArrayList<>(list.size());
    for (Object o : list) {
      BookingSettings.Window w = window(o);
      if (w == null) {
        return List.of();
      }
      windows.add(w);
    }
    return windows;
  }

  /** {@code { day: 1 à 7, start: "HH:mm", end: "HH:mm" }}, end après start, sinon null. */
  private static BookingSettings.Window window(Object value) {
    if (!(value instanceof Map<?, ?> m)
        || !(m.get("day") instanceof Number day)
        || !(day instanceof Integer || day instanceof Long)
        || day.longValue() < 1
        || day.longValue() > 7) {
      return null;
    }
    LocalTime start = time(m.get("start"));
    LocalTime end = time(m.get("end"));
    if (start == null || end == null || !end.isAfter(start)) {
      return null;
    }
    return new BookingSettings.Window(DayOfWeek.of(day.intValue()), start, end);
  }

  private static LocalTime time(Object value) {
    if (!(value instanceof String s) || !TIME.matcher(s).matches()) {
      return null;
    }
    try {
      return LocalTime.parse(s);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
