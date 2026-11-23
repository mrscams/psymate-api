package com.psymate.seed;

import com.psymate.seed.ApiClient.Response;
import com.psymate.seed.Plan.CreateEntry;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.Plan.Step;
import com.psymate.seed.StepExecutor.IncompleteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Écrit la démonstration en arabe (M16-06) par l'API, comme le reste du seed, sans aucune écriture
 * SQL :
 *
 * <ol>
 *   <li>compte {@link Accounts#ARABIC} : connexion, sinon inscription avec {@code Accept-Language:
 *       ar} (la colonne {@code practitioner.locale} vaut donc {@code ar}), e-mail de confirmation
 *       lu dans Mailpit, connexion ; le compte n'est ni approuvé ni publié ;
 *   <li>préférence {@code locale = ar} ({@code PATCH /api/me/preferences}), si elle n'y est pas
 *       déjà ;
 *   <li>dossier {@link ArabicStory} ({@code POST /api/patients}, séances, entrées, liens), s'il
 *       n'existe pas déjà chez elle sous son nom d'affichage.
 * </ol>
 *
 * <p>Contrôle final, en lecture, avec {@code Accept-Language: ar} : la préférence vaut {@code ar},
 * chaque titre du plan se relit tel quel dans la timeline, et les catalogues portent leur libellé
 * arabe ({@code labelAr} des 29 émotions globales, des types d'entrée et des types de lien, {@code
 * inverseLabelAr} des types de lien orientés).
 */
public final class ArabicSeeder {

  /** Ce que lit le contrôle des catalogues. */
  public record Catalogues(
      int emotions,
      int emotionsWithLabelAr,
      int entryTypes,
      int entryTypesWithLabelAr,
      int linkTypes,
      int linkTypesWithLabelAr,
      int directedLinkTypes,
      int directedWithInverseLabelAr,
      List<String> problems) {}

  /** Bilan de la démonstration en arabe. */
  public record Result(
      String email,
      String locale,
      boolean localeSetNow,
      String displayName,
      String patientId,
      StepExecutor.Result written,
      Catalogues catalogues,
      List<String> problems) {

    public boolean ok() {
      return problems.isEmpty() && catalogues.problems().isEmpty();
    }

    public String describe() {
      String dossier =
          written == null
              ? "déjà présent"
              : "créé (%d séances, %d entrées, %d liens)"
                  .formatted(written.sessions(), written.entries(), written.links());
      List<String> all = new ArrayList<>(problems);
      all.addAll(catalogues.problems());
      return ("Démonstration en arabe : %s, préférence locale = %s (%s) ; dossier « %s » %s ;"
              + " catalogues lus avec Accept-Language: ar : labelAr sur %d/%d émotions globales,"
              + " %d/%d types d'entrée, %d/%d types de lien, inverseLabelAr sur %d/%d types"
              + " orientés%s")
          .formatted(
              email,
              locale,
              localeSetNow ? "posée maintenant" : "déjà posée",
              displayName,
              dossier,
              catalogues.emotionsWithLabelAr(),
              catalogues.emotions(),
              catalogues.entryTypesWithLabelAr(),
              catalogues.entryTypes(),
              catalogues.linkTypesWithLabelAr(),
              catalogues.linkTypes(),
              catalogues.directedWithInverseLabelAr(),
              catalogues.directedLinkTypes(),
              all.isEmpty() ? " : OK" : " ; écarts : " + String.join(", ", all) + " : ÉCHEC");
    }
  }

  private final Accounts accounts;
  private ApiClient api;

  public ArabicSeeder(Accounts accounts) {
    this.accounts = accounts;
  }

  /** Session de la praticienne arabophone, ouverte par {@link #seed} (pour le contrôle final). */
  public ApiClient client() {
    if (api == null) {
      throw new IllegalStateException("seed() n'a pas encore été appelé");
    }
    return api;
  }

  public Result seed() {
    api = accounts.login(Accounts.ARABIC);
    boolean localeSetNow = false;
    if (needsLocale(api.get("/api/me/preferences", 200).json())) {
      api.patch("/api/me/preferences", localePatch(), 200);
      localeSetNow = true;
    }

    PatientPlan plan = ArabicStory.build(Scenario.today());
    String patientId = Runner.listPatients(api).get(plan.displayName());
    StepExecutor.Result written = null;
    if (patientId == null) {
      try {
        written = StepExecutor.execute(plan, this::call, null);
      } catch (IncompleteException e) {
        // dossier à moitié écrit : on l'archive pour qu'une relance le recrée proprement
        api.delete("/api/patients/" + e.patientId());
        throw new SeedException(
            "Patient « "
                + plan.displayName()
                + " » : "
                + e.getMessage()
                + " (dossier partiel archivé)",
            e);
      }
      patientId = written.patientId();
    }

    List<String> problems = new ArrayList<>();
    Object locale = api.get("/api/me/preferences", 200).json().get("locale");
    if (!ArabicStory.LOCALE.equals(locale)) {
      problems.add("préférence locale = " + locale);
    }
    for (String title : missingTitles(plan, timelineTitles(api, patientId))) {
      problems.add("titre absent de la timeline : « " + title + " »");
    }
    Catalogues catalogues =
        catalogues(
            list(api.get("/api/vocabulary/emotions", 200)),
            list(api.get("/api/vocabulary/entry-types", 200)),
            list(api.get("/api/vocabulary/link-types", 200)));
    return new Result(
        Accounts.ARABIC.email(),
        String.valueOf(locale),
        localeSetNow,
        plan.displayName(),
        patientId,
        written,
        catalogues,
        List.copyOf(problems));
  }

  private Response call(String method, String path, Object body) {
    return switch (method) {
      case "POST" -> api.post(path, body);
      case "PATCH" -> api.patch(path, body);
      case "GET" -> api.get(path);
      default -> throw new IllegalArgumentException(method);
    };
  }

  // ---- décisions pures (testées sans réseau)

  /** Vrai si la préférence {@code locale} lue ne vaut pas déjà {@code ar}. */
  static boolean needsLocale(Map<String, Object> preferences) {
    return !ArabicStory.LOCALE.equals(preferences.get("locale"));
  }

  /** Corps exact du {@code PATCH /api/me/preferences} : la seule clé {@code locale}. */
  static Map<String, Object> localePatch() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("locale", ArabicStory.LOCALE);
    return body;
  }

  /** Titres des entrées du plan, dans l'ordre d'écriture. */
  static List<String> titles(PatientPlan plan) {
    List<String> titles = new ArrayList<>();
    for (Step step : plan.steps()) {
      if (step instanceof CreateEntry e && e.body().get("title") instanceof String t) {
        titles.add(t);
      }
    }
    return titles;
  }

  /** Titres du plan qui ne se relisent pas tels quels dans la timeline. */
  static List<String> missingTitles(PatientPlan plan, Collection<String> seen) {
    return titles(plan).stream().filter(t -> !seen.contains(t)).toList();
  }

  /**
   * Contrôle des catalogues : {@code labelAr} sur chaque émotion globale ({@code scope = global}),
   * chaque type d'entrée et chaque type de lien ; {@code inverseLabelAr} sur chaque type de lien
   * orienté ({@code directed = true}). Les émotions personnalisées n'ont qu'un libellé (G Q98).
   */
  static Catalogues catalogues(List<?> emotions, List<?> entryTypes, List<?> linkTypes) {
    List<String> problems = new ArrayList<>();
    int globals = 0;
    int emotionsAr = 0;
    for (Object o : emotions) {
      if (o instanceof Map<?, ?> m && "global".equals(m.get("scope"))) {
        globals++;
        if (filled(m.get("labelAr"))) {
          emotionsAr++;
        } else {
          problems.add("émotion " + m.get("code") + " sans labelAr");
        }
      }
    }
    int typesAr = 0;
    for (Object o : entryTypes) {
      if (o instanceof Map<?, ?> m) {
        if (filled(m.get("labelAr"))) {
          typesAr++;
        } else {
          problems.add("type d'entrée " + m.get("code") + " sans labelAr");
        }
      }
    }
    int linksAr = 0;
    int directed = 0;
    int inverseAr = 0;
    for (Object o : linkTypes) {
      if (o instanceof Map<?, ?> m) {
        if (filled(m.get("labelAr"))) {
          linksAr++;
        } else {
          problems.add("type de lien " + m.get("code") + " sans labelAr");
        }
        if (Boolean.TRUE.equals(m.get("directed"))) {
          directed++;
          if (filled(m.get("inverseLabelAr"))) {
            inverseAr++;
          } else {
            problems.add("type de lien " + m.get("code") + " sans inverseLabelAr");
          }
        }
      }
    }
    if (globals == 0 || entryTypes.isEmpty() || linkTypes.isEmpty()) {
      problems.add("catalogue vide");
    }
    return new Catalogues(
        globals,
        emotionsAr,
        entryTypes.size(),
        typesAr,
        linkTypes.size(),
        linksAr,
        directed,
        inverseAr,
        List.copyOf(problems));
  }

  private static boolean filled(Object value) {
    return value instanceof String s && !s.isBlank();
  }

  // ---- lectures

  private static List<?> list(Response response) {
    return response.value() instanceof List<?> l ? l : List.of();
  }

  /** Titres de toutes les entrées de la timeline (200 par page, entrées privées comprises). */
  private static List<String> timelineTitles(ApiClient api, String patientId) {
    List<String> titles = new ArrayList<>();
    String cursor = null;
    do {
      String path =
          "/api/patients/%s/timeline?limit=200&includePrivate=true%s"
              .formatted(patientId, cursor == null ? "" : "&cursor=" + Runner.encode(cursor));
      Map<String, Object> page = api.get(path, 200).json();
      if (page.get("items") instanceof List<?> items) {
        for (Object o : items) {
          if (o instanceof Map<?, ?> item && item.get("title") instanceof String t) {
            titles.add(t);
          }
        }
      }
      cursor = page.get("nextCursor") == null ? null : page.get("nextCursor").toString();
    } while (cursor != null);
    return titles;
  }
}
