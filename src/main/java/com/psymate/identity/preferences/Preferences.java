package com.psymate.identity.preferences;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Règles pures des préférences par compte (fiche 14, A10.2 ; agenda : G Q57 ; prise de rendez-vous
 * en ligne : Q75) : clés connues, domaines, défauts par profession et merge RFC 7396. Les
 * préférences sont stockées à plat dans practitioner.preferences.
 */
public final class Preferences {

  /** Violation de domaine : clé fautive et code de message. */
  public record Violation(String key, String code) {}

  /** Résultat d'un merge : nouveau contenu stocké, clés réellement changées, violations. */
  public record MergeResult(
      Map<String, Object> stored, List<String> changedKeys, List<Violation> violations) {}

  public static final String OVERVIEW_VIEW = "overview.view";
  public static final String OVERVIEW_WINDOW = "overview.window";
  public static final String TIMELINE_GROUP_BY = "timeline.groupBy";
  public static final String THEME = "theme";
  public static final String LOCALE = "locale";
  public static final String SUBJECT_LABEL = "vocabulary.subjectLabel";
  public static final String MEDICATION_VISIBLE = "entryTypes.medicationVisible";
  public static final String DEFAULT_VISIBILITY = "entries.defaultVisibility";
  public static final String DEFAULT_DURATION = "sessions.defaultDurationMin";
  public static final String AGENDA_DAY_START_HOUR = "agenda.dayStartHour";
  public static final String AGENDA_DAY_END_HOUR = "agenda.dayEndHour";
  public static final String AGENDA_WORKING_DAYS = "agenda.workingDays";
  public static final String AGENDA_DEFAULT_VIEW = "agenda.defaultView";
  public static final String BOOKING_ENABLED = "booking.enabled";
  public static final String BOOKING_CONFIRMATION = "booking.confirmation";
  public static final String BOOKING_WINDOWS = "booking.windows";
  public static final String BOOKING_SLOT_DURATION_MIN = "booking.slotDurationMin";
  public static final String BOOKING_MIN_NOTICE_HOURS = "booking.minNoticeHours";
  public static final String BOOKING_HORIZON_DAYS = "booking.horizonDays";
  public static final String BOOKING_MODALITIES = "booking.modalities";
  public static final String BOOKING_TIMEZONE = "booking.timezone";

  /** Nombre de clés connues (toutes toujours présentes en lecture). */
  public static final int KEY_COUNT = 21;

  /** Nombre maximal de plages de prise de rendez-vous (Q75). */
  static final int MAX_BOOKING_WINDOWS = 21;

  static final String INVALID_VALUE = "INVALID_VALUE";
  static final String OUT_OF_RANGE = "OUT_OF_RANGE";

  private static final String WINDOW_DAY = "day";
  private static final String WINDOW_START = "start";
  private static final String WINDOW_END = "end";
  private static final Set<String> WINDOW_FIELDS = Set.of(WINDOW_DAY, WINDOW_START, WINDOW_END);

  /** HH:mm strict, heures 00 à 23. */
  private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):([0-5]\\d)");

  /** Ordre des plages : jour, puis début. */
  private static final Comparator<int[]> WINDOW_ORDER =
      Comparator.<int[]>comparingInt(w -> w[0]).thenComparingInt(w -> w[1]);

  private static final Set<String> MODALITIES = Set.of("in_person", "video", "phone");

  /** Identifiants tz connus de java.time, copiés une fois. */
  private static final Set<String> ZONE_IDS = Set.copyOf(ZoneId.getAvailableZoneIds());

  private static final Map<String, Set<String>> ENUMS =
      Map.of(
          OVERVIEW_VIEW, Set.of("simple", "complete"),
          OVERVIEW_WINDOW, Set.of("30d", "90d", "12m"),
          TIMELINE_GROUP_BY, Set.of("session", "month", "chronological"),
          THEME, Set.of("light", "soft", "dark", "system"),
          LOCALE, Set.of("fr", "en", "ar"),
          SUBJECT_LABEL, Set.of("patient", "client"),
          DEFAULT_VISIBILITY, Set.of("clinical", "private"),
          AGENDA_DEFAULT_VIEW, Set.of("day", "week", "month", "list"),
          BOOKING_CONFIRMATION, Set.of("manual", "auto"));

  private Preferences() {}

  /**
   * Défauts calculés pour une profession (E5.4, G3, Q23, Q57, Q75), dans l'ordre de la fiche 14.
   */
  public static Map<String, Object> defaults(String profession) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put(OVERVIEW_VIEW, "simple");
    d.put(OVERVIEW_WINDOW, "90d");
    d.put(TIMELINE_GROUP_BY, "session");
    d.put(THEME, "light");
    d.put(LOCALE, "fr");
    d.put(SUBJECT_LABEL, "coach".equals(profession) ? "client" : "patient");
    d.put(MEDICATION_VISIBLE, "psychiatrist".equals(profession));
    d.put(DEFAULT_VISIBILITY, "clinical");
    d.put(DEFAULT_DURATION, 60);
    d.put(AGENDA_DAY_START_HOUR, 8);
    d.put(AGENDA_DAY_END_HOUR, 19);
    d.put(AGENDA_WORKING_DAYS, List.of(1, 2, 3, 4, 5, 6));
    d.put(AGENDA_DEFAULT_VIEW, "week");
    d.put(BOOKING_ENABLED, false);
    d.put(BOOKING_CONFIRMATION, "manual");
    d.put(BOOKING_WINDOWS, List.of());
    d.put(BOOKING_SLOT_DURATION_MIN, 60);
    d.put(BOOKING_MIN_NOTICE_HOURS, 24);
    d.put(BOOKING_HORIZON_DAYS, 28);
    d.put(BOOKING_MODALITIES, List.of("in_person"));
    d.put(BOOKING_TIMEZONE, "Europe/Paris");
    return d;
  }

  /**
   * Valeurs stockées valides complétées par les défauts ; les clés inconnues sont écartées. Si les
   * bornes de la journée stockées sont incohérentes (fin non strictement après le début), les deux
   * reviennent à leur défaut.
   */
  public static Map<String, Object> effective(String profession, Map<String, Object> stored) {
    Map<String, Object> result = defaults(profession);
    if (stored != null) {
      stored.forEach(
          (k, v) -> {
            if (result.containsKey(k) && v != null && isValid(k, v)) {
              result.put(k, normalize(k, v));
            }
          });
    }
    if (!dayBoundsConsistent(result)) {
      result.put(AGENDA_DAY_START_HOUR, 8);
      result.put(AGENDA_DAY_END_HOUR, 19);
    }
    return result;
  }

  static boolean isValid(String key, Object value) {
    return violation(key, value) == null;
  }

  /** Code de violation d'une valeur pour une clé connue, ou null si elle est admise. */
  static String violation(String key, Object value) {
    if (ENUMS.containsKey(key)) {
      return value instanceof String s && ENUMS.get(key).contains(s) ? null : INVALID_VALUE;
    }
    return switch (key) {
      case MEDICATION_VISIBLE, BOOKING_ENABLED -> value instanceof Boolean ? null : INVALID_VALUE;
      case DEFAULT_DURATION -> isIntegral(value) && inRange(value, 15, 180) ? null : INVALID_VALUE;
      case AGENDA_DAY_START_HOUR -> integralInRange(value, 0, 23);
      case AGENDA_DAY_END_HOUR -> integralInRange(value, 1, 24);
      case AGENDA_WORKING_DAYS -> workingDaysViolation(value);
      case BOOKING_WINDOWS -> bookingWindowsViolation(value);
      case BOOKING_SLOT_DURATION_MIN -> slotDurationViolation(value);
      case BOOKING_MIN_NOTICE_HOURS -> integralInRange(value, 1, 168);
      case BOOKING_HORIZON_DAYS -> integralInRange(value, 7, 90);
      case BOOKING_MODALITIES -> modalitiesViolation(value);
      case BOOKING_TIMEZONE -> isRegionalZone(value) ? null : INVALID_VALUE;
      default -> INVALID_VALUE;
    };
  }

  private static String integralInRange(Object value, long min, long max) {
    if (!isIntegral(value)) {
      return INVALID_VALUE;
    }
    return inRange(value, min, max) ? null : OUT_OF_RANGE;
  }

  /** Liste non vide d'entiers de 1 (lundi) à 7 (dimanche), sans doublon ; ordre libre. */
  private static String workingDaysViolation(Object value) {
    if (!(value instanceof List<?> days) || days.isEmpty()) {
      return INVALID_VALUE;
    }
    Set<Integer> seen = new HashSet<>();
    for (Object day : days) {
      if (!isIntegral(day)) {
        return INVALID_VALUE;
      }
      if (!inRange(day, 1, 7)) {
        return OUT_OF_RANGE;
      }
      if (!seen.add(((Number) day).intValue())) {
        return INVALID_VALUE;
      }
    }
    return null;
  }

  /**
   * Entier de 15 à 180, multiple de 5 ; un non-entier est INVALID_VALUE (comme integralInRange).
   */
  private static String slotDurationViolation(Object value) {
    String code = integralInRange(value, 15, 180);
    if (code != null) {
      return code;
    }
    return ((Number) value).longValue() % 5 == 0 ? null : OUT_OF_RANGE;
  }

  /** Liste non vide, sans doublon, d'éléments parmi in_person, video, phone ; ordre libre. */
  private static String modalitiesViolation(Object value) {
    if (!(value instanceof List<?> modalities) || modalities.isEmpty()) {
      return INVALID_VALUE;
    }
    Set<Object> seen = new HashSet<>();
    for (Object m : modalities) {
      if (!(m instanceof String s) || !MODALITIES.contains(s) || !seen.add(s)) {
        return INVALID_VALUE;
      }
    }
    return null;
  }

  /**
   * Identifiant de la base IANA (tz) connu de java.time (par exemple Europe/Paris) : les décalages
   * ("+02:00", "UTC+1") et les noms incomplets ("Paris") sont refusés.
   */
  private static boolean isRegionalZone(Object value) {
    return value instanceof String s && ZONE_IDS.contains(s);
  }

  /**
   * Plages de prise de rendez-vous (Q75) : liste d'au plus 21 objets {day, start, end} ; day entier
   * de 1 (lundi) à 7, start et end en HH:mm strict (minutes multiples de 5), end strictement après
   * start, pas de chevauchement le même jour (deux plages contiguës sont admises). Plus de 21
   * plages : OUT_OF_RANGE ; toute autre faute : INVALID_VALUE. Liste vide admise.
   */
  private static String bookingWindowsViolation(Object value) {
    if (!(value instanceof List<?> windows)) {
      return INVALID_VALUE;
    }
    if (windows.size() > MAX_BOOKING_WINDOWS) {
      return OUT_OF_RANGE;
    }
    List<int[]> parsed = new ArrayList<>(windows.size());
    for (Object w : windows) {
      int[] window = parseWindow(w);
      if (window == null) {
        return INVALID_VALUE;
      }
      parsed.add(window);
    }
    parsed.sort(WINDOW_ORDER);
    for (int i = 1; i < parsed.size(); i++) {
      int[] previous = parsed.get(i - 1);
      int[] current = parsed.get(i);
      if (previous[0] == current[0] && current[1] < previous[2]) {
        return INVALID_VALUE;
      }
    }
    return null;
  }

  /** {jour, début en minutes, fin en minutes}, ou null si la plage est mal formée. */
  private static int[] parseWindow(Object value) {
    if (!(value instanceof Map<?, ?> m) || !WINDOW_FIELDS.equals(m.keySet())) {
      return null;
    }
    Object day = m.get(WINDOW_DAY);
    if (!isIntegral(day) || !inRange(day, 1, 7)) {
      return null;
    }
    int start = minutesOf(m.get(WINDOW_START));
    int end = minutesOf(m.get(WINDOW_END));
    if (start < 0 || end < 0 || end <= start) {
      return null;
    }
    return new int[] {((Number) day).intValue(), start, end};
  }

  /** Minutes depuis minuit d'un HH:mm strict aux minutes multiples de 5, ou -1. */
  private static int minutesOf(Object value) {
    if (!(value instanceof String s)) {
      return -1;
    }
    Matcher t = TIME.matcher(s);
    if (!t.matches()) {
      return -1;
    }
    int minutes = Integer.parseInt(t.group(2));
    return minutes % 5 == 0 ? Integer.parseInt(t.group(1)) * 60 + minutes : -1;
  }

  private static boolean isIntegral(Object value) {
    return value instanceof Integer || value instanceof Long;
  }

  private static boolean inRange(Object value, long min, long max) {
    long v = ((Number) value).longValue();
    return v >= min && v <= max;
  }

  /**
   * Forme canonique d'une valeur déjà validée : entiers ramenés à Integer, jours travaillés en
   * List<Integer> et modalités en List<String> dans l'ordre reçu, plages en objets ordonnés {day,
   * start, end} triés par jour puis début.
   */
  private static Object normalize(String key, Object value) {
    if (value instanceof Number n) {
      return n.intValue();
    }
    return switch (key) {
      case AGENDA_WORKING_DAYS -> {
        List<Integer> days = new ArrayList<>();
        for (Object o : (List<?>) value) {
          days.add(((Number) o).intValue());
        }
        yield List.copyOf(days);
      }
      case BOOKING_MODALITIES -> {
        List<String> modalities = new ArrayList<>();
        for (Object o : (List<?>) value) {
          modalities.add((String) o);
        }
        yield List.copyOf(modalities);
      }
      case BOOKING_WINDOWS -> normalizeWindows((List<?>) value);
      default -> value;
    };
  }

  private static List<Map<String, Object>> normalizeWindows(List<?> windows) {
    List<int[]> parsed = new ArrayList<>(windows.size());
    List<Map<String, Object>> sorted = new ArrayList<>(windows.size());
    for (Object w : windows) {
      parsed.add(parseWindow(w));
    }
    parsed.sort(WINDOW_ORDER);
    for (int[] w : parsed) {
      Map<String, Object> window = new LinkedHashMap<>();
      window.put(WINDOW_DAY, w[0]);
      window.put(WINDOW_START, hhmm(w[1]));
      window.put(WINDOW_END, hhmm(w[2]));
      sorted.add(Collections.unmodifiableMap(window));
    }
    return List.copyOf(sorted);
  }

  private static String hhmm(int minutes) {
    int h = minutes / 60;
    int m = minutes % 60;
    return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
  }

  private static boolean dayBoundsConsistent(Map<String, Object> values) {
    return ((Number) values.get(AGENDA_DAY_END_HOUR)).intValue()
        > ((Number) values.get(AGENDA_DAY_START_HOUR)).intValue();
  }

  /**
   * Merge RFC 7396 sur les clés connues : null supprime (retour au défaut), clé inconnue ignorée,
   * valeur hors domaine = violation (rien n'est alors appliqué par l'appelant). Après fusion, la
   * fin de journée effective doit rester strictement après le début : sinon OUT_OF_RANGE sur
   * agenda.dayEndHour (Q57).
   */
  public static MergeResult merge(Map<String, Object> stored, Map<String, Object> patch) {
    Map<String, Object> next = new LinkedHashMap<>(stored == null ? Map.of() : stored);
    List<String> changed = new ArrayList<>();
    List<Violation> violations = new ArrayList<>();
    Map<String, Object> defaults = defaults(null);
    for (Map.Entry<String, Object> e : patch.entrySet()) {
      String key = e.getKey();
      if (!defaults.containsKey(key)) {
        continue;
      }
      Object value = e.getValue();
      if (value == null) {
        if (next.remove(key) != null) {
          changed.add(key);
        }
        continue;
      }
      String code = violation(key, value);
      if (code != null) {
        violations.add(new Violation(key, code));
        continue;
      }
      Object normalized = normalize(key, value);
      Object before = next.put(key, normalized);
      if (!normalized.equals(before)) {
        changed.add(key);
      }
    }
    boolean touchesDayBounds =
        patch.containsKey(AGENDA_DAY_START_HOUR) || patch.containsKey(AGENDA_DAY_END_HOUR);
    boolean boundsAlreadyFaulty =
        violations.stream()
            .anyMatch(
                v -> AGENDA_DAY_START_HOUR.equals(v.key()) || AGENDA_DAY_END_HOUR.equals(v.key()));
    if (touchesDayBounds && !boundsAlreadyFaulty) {
      Map<String, Object> effectiveBounds = new LinkedHashMap<>(defaults);
      effectiveBounds.putAll(next);
      if (!dayBoundsConsistent(effectiveBounds)) {
        violations.add(new Violation(AGENDA_DAY_END_HOUR, OUT_OF_RANGE));
        changed.remove(AGENDA_DAY_START_HOUR);
        changed.remove(AGENDA_DAY_END_HOUR);
      }
    }
    return new MergeResult(next, changed, violations);
  }
}
