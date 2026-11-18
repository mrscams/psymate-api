package com.psymate.seed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Contrôle final en LECTURE SEULE, par l'API : pour chaque patient, pagine la timeline (200 par
 * page, entrées privées comprises) et vérifie que {@code authorType} vaut « practitioner » partout
 * et que toute entrée autre qu'une séance porte un {@code sessionId} (E5.5, A11). Lit aussi le
 * Profil 360 ({@code GET /api/patients/{id}/profile-items}) et vérifie le tri de J3 et la règle de
 * période (M13-03).
 */
public final class Verifier {

  /** Résultat du contrôle. */
  public record Report(int patients, int entries, int profileItems, List<String> violations) {

    public boolean ok() {
      return violations.isEmpty();
    }

    public String describe() {
      if (ok()) {
        return "Contrôle : %d entrées, author_type = practitioner partout, session_id présent partout ; %d informations de profil, triées, période seulement dans history : OK"
            .formatted(entries, profileItems);
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
    int profileItems = 0;
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
      profileItems += checkProfile(patientId, api, violations);
    }
    return new Report(patients.size(), total, profileItems, violations);
  }

  private static final List<String> CATEGORIES =
      List.of("history", "family", "health", "values_goals");

  /**
   * Tri de J3 : rubrique dans l'ordre {@link #CATEGORIES} ; dans {@code history}, {@code
   * periodYear} croissant (absents en dernier) puis {@code createdAt} ; ailleurs {@code createdAt}.
   *
   * @return le nombre d'informations lues
   */
  private static int checkProfile(String patientId, ApiClient api, List<String> violations) {
    Map<String, Object> profile =
        api.get("/api/patients/%s/profile-items".formatted(patientId), 200).json();
    if (!patientId.equals(String.valueOf(profile.get("patientId")))) {
      violations.add("patient %s : profil d'un autre patient".formatted(patientId));
    }
    if (!(profile.get("items") instanceof List<?> items)) {
      violations.add("patient %s : profil sans items".formatted(patientId));
      return 0;
    }
    Map<?, ?> previous = null;
    for (Object o : items) {
      if (!(o instanceof Map<?, ?> item)) {
        continue;
      }
      if (!CATEGORIES.contains(item.get("category"))) {
        violations.add(
            "patient %s, profil %s : rubrique %s"
                .formatted(patientId, item.get("id"), item.get("category")));
        continue;
      }
      if (!"history".equals(item.get("category"))
          && (item.get("periodLabel") != null || item.get("periodYear") != null)) {
        violations.add(
            "patient %s, profil %s : période hors de history".formatted(patientId, item.get("id")));
      }
      if (previous != null && compare(previous, item) > 0) {
        violations.add(
            "patient %s, profil %s : rangé après %s, tri de J3 non respecté"
                .formatted(patientId, item.get("id"), previous.get("id")));
      }
      previous = item;
    }
    return items.size();
  }

  private static int compare(Map<?, ?> a, Map<?, ?> b) {
    int byCategory =
        Integer.compare(
            CATEGORIES.indexOf(a.get("category")), CATEGORIES.indexOf(b.get("category")));
    if (byCategory != 0) {
      return byCategory;
    }
    if ("history".equals(a.get("category"))) {
      Long ya = a.get("periodYear") instanceof Number n ? n.longValue() : null;
      Long yb = b.get("periodYear") instanceof Number n ? n.longValue() : null;
      if (ya == null && yb != null) {
        return 1;
      }
      if (ya != null && yb == null) {
        return -1;
      }
      if (ya != null && !ya.equals(yb)) {
        return Long.compare(ya, yb);
      }
    }
    return Instant.parse(String.valueOf(a.get("createdAt")))
        .compareTo(Instant.parse(String.valueOf(b.get("createdAt"))));
  }
}
