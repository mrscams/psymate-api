package com.psymate.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contrôle final en LECTURE SEULE, par l'API : pour chaque patient, pagine la timeline (200 par
 * page, entrées privées comprises) et vérifie que {@code authorType} vaut « practitioner » partout
 * et que toute entrée autre qu'une séance porte un {@code sessionId} (E5.5, A11).
 */
public final class Verifier {

  /** Résultat du contrôle. */
  public record Report(int patients, int entries, List<String> violations) {

    public boolean ok() {
      return violations.isEmpty();
    }

    public String describe() {
      if (ok()) {
        return "Contrôle : %d entrées, author_type = practitioner partout, session_id présent partout : OK"
            .formatted(entries);
      }
      StringBuilder sb =
          new StringBuilder(
              "Contrôle : %d entrées, %d violation(s) :".formatted(entries, violations.size()));
      violations.stream().limit(30).forEach(v -> sb.append("\n  - ").append(v));
      return sb.toString();
    }
  }

  private Verifier() {}

  /**
   * @param patients identifiant de patient vers le client (session) de son praticien
   */
  public static Report verify(Map<String, ApiClient> patients) {
    List<String> violations = new ArrayList<>();
    int total = 0;
    for (Map.Entry<String, ApiClient> e : patients.entrySet()) {
      String patientId = e.getKey();
      ApiClient api = e.getValue();
      String cursor = null;
      do {
        String path =
            "/api/patients/%s/timeline?limit=200&includePrivate=true%s"
                .formatted(patientId, cursor == null ? "" : "&cursor=" + Runner.encode(cursor));
        Map<String, Object> page = api.get(path, 200).json();
        if (page.get("items") instanceof List<?> items) {
          for (Object o : items) {
            if (!(o instanceof Map<?, ?> item)) {
              continue;
            }
            total++;
            Object type = item.get("type");
            if (!"practitioner".equals(item.get("authorType"))) {
              violations.add(
                  "patient %s, entrée %s : authorType = %s"
                      .formatted(patientId, item.get("id"), item.get("authorType")));
            }
            if (!"session".equals(type) && item.get("sessionId") == null) {
              violations.add(
                  "patient %s, entrée %s (%s) : sessionId absent"
                      .formatted(patientId, item.get("id"), type));
            }
          }
        }
        cursor = page.get("nextCursor") == null ? null : page.get("nextCursor").toString();
      } while (cursor != null);
    }
    return new Report(patients.size(), total, violations);
  }
}
