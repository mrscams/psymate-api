package com.psymate.identity.preferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Règles pures des préférences par compte (fiche 14, A10.2 ; agenda : G Q57) : clés connues,
 * domaines, défauts par profession et merge RFC 7396. Les préférences sont stockées à plat dans
 * practitioner.preferences.
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

  /** Nombre de clés connues (toutes toujours présentes en lecture). */
  public static final int KEY_COUNT = 13;

  static final String INVALID_VALUE = "INVALID_VALUE";
  static final String OUT_OF_RANGE = "OUT_OF_RANGE";

  private static final Map<String, Set<String>> ENUMS =
      Map.of(
          OVERVIEW_VIEW, Set.of("simple", "complete"),
          OVERVIEW_WINDOW, Set.of("30d", "90d", "12m"),
          TIMELINE_GROUP_BY, Set.of("session", "month", "chronological"),
          THEME, Set.of("light", "soft", "dark", "system"),
          LOCALE, Set.of("fr", "en"),
          SUBJECT_LABEL, Set.of("patient", "client"),
          DEFAULT_VISIBILITY, Set.of("clinical", "private"),
          AGENDA_DEFAULT_VIEW, Set.of("day", "week", "month", "list"));

  private Preferences() {}

  /** Défauts calculés pour une profession (E5.4, G3, Q23, Q57), dans l'ordre de la fiche 14. */
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
              result.put(k, normalize(v));
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
      case MEDICATION_VISIBLE -> value instanceof Boolean ? null : INVALID_VALUE;
      case DEFAULT_DURATION -> isIntegral(value) && inRange(value, 15, 180) ? null : INVALID_VALUE;
      case AGENDA_DAY_START_HOUR -> integralInRange(value, 0, 23);
      case AGENDA_DAY_END_HOUR -> integralInRange(value, 1, 24);
      case AGENDA_WORKING_DAYS -> workingDaysViolation(value);
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

  private static boolean isIntegral(Object value) {
    return value instanceof Integer || value instanceof Long;
  }

  private static boolean inRange(Object value, long min, long max) {
    long v = ((Number) value).longValue();
    return v >= min && v <= max;
  }

  /** Entiers ramenés à Integer, listes de jours à des List<Integer> dans l'ordre reçu. */
  private static Object normalize(Object value) {
    if (value instanceof Number n) {
      return n.intValue();
    }
    if (value instanceof List<?> list) {
      List<Integer> days = new ArrayList<>(list.size());
      for (Object o : list) {
        days.add(((Number) o).intValue());
      }
      return List.copyOf(days);
    }
    return value;
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
      Object normalized = normalize(value);
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
