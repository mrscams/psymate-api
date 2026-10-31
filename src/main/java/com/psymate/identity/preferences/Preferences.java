package com.psymate.identity.preferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Règles pures des préférences par compte (fiche 14, A10.2) : clés connues, domaines, défauts par
 * profession et merge RFC 7396. Les préférences sont stockées à plat dans practitioner.preferences.
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

  private static final Map<String, Set<String>> ENUMS =
      Map.of(
          OVERVIEW_VIEW, Set.of("simple", "complete"),
          OVERVIEW_WINDOW, Set.of("30d", "90d", "12m"),
          TIMELINE_GROUP_BY, Set.of("session", "month", "chronological"),
          THEME, Set.of("light", "soft", "dark", "system"),
          LOCALE, Set.of("fr", "en"),
          SUBJECT_LABEL, Set.of("patient", "client"),
          DEFAULT_VISIBILITY, Set.of("clinical", "private"));

  private Preferences() {}

  /** Défauts calculés pour une profession (E5.4, G3, Q23), dans l'ordre de la fiche 14. */
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
    return d;
  }

  /** Valeurs stockées valides complétées par les défauts ; les clés inconnues sont écartées. */
  public static Map<String, Object> effective(String profession, Map<String, Object> stored) {
    Map<String, Object> result = defaults(profession);
    if (stored != null) {
      stored.forEach(
          (k, v) -> {
            if (result.containsKey(k) && v != null && isValid(k, v)) {
              result.put(k, v instanceof Number n ? (Object) n.intValue() : v);
            }
          });
    }
    return result;
  }

  static boolean isValid(String key, Object value) {
    if (ENUMS.containsKey(key)) {
      return value instanceof String s && ENUMS.get(key).contains(s);
    }
    return switch (key) {
      case MEDICATION_VISIBLE -> value instanceof Boolean;
      case DEFAULT_DURATION ->
          (value instanceof Integer || value instanceof Long)
              && ((Number) value).longValue() >= 15
              && ((Number) value).longValue() <= 180;
      default -> false;
    };
  }

  /**
   * Merge RFC 7396 sur les clés connues : null supprime (retour au défaut), clé inconnue ignorée,
   * valeur hors domaine = violation (rien n'est alors appliqué par l'appelant).
   */
  public static MergeResult merge(Map<String, Object> stored, Map<String, Object> patch) {
    Map<String, Object> next = new LinkedHashMap<>(stored == null ? Map.of() : stored);
    List<String> changed = new ArrayList<>();
    List<Violation> violations = new ArrayList<>();
    for (Map.Entry<String, Object> e : patch.entrySet()) {
      String key = e.getKey();
      if (!defaults(null).containsKey(key)) {
        continue;
      }
      Object value = e.getValue();
      if (value == null) {
        if (next.remove(key) != null) {
          changed.add(key);
        }
      } else if (!isValid(key, value)) {
        violations.add(new Violation(key, "INVALID_VALUE"));
      } else {
        Object normalized = value instanceof Number n ? (Object) n.intValue() : value;
        Object before = next.put(key, normalized);
        if (!normalized.equals(before)) {
          changed.add(key);
        }
      }
    }
    return new MergeResult(next, changed, violations);
  }
}
