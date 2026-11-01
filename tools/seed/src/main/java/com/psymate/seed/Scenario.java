package com.psymate.seed;

import com.psymate.seed.Plan.CloseSession;
import com.psymate.seed.Plan.CreateEntry;
import com.psymate.seed.Plan.LinkPlan;
import com.psymate.seed.Plan.PatchEntry;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.Plan.Practitioner;
import com.psymate.seed.Plan.StartSession;
import com.psymate.seed.Plan.Step;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Générateur PUR et déterministe des jeux de données (C3, E5.5) : aucun accès réseau, aucune
 * horloge cachée (la date du jour est un paramètre, tronquée au jour). Réutilisé tel quel par le
 * {@link Runner} (HTTP) et par les tests de performance (MockMvc).
 *
 * <p>Tailles : {@link #small} (5 patients, environ 200 entrées chacun), {@link #medium} (100
 * patients ordinaires de environ 200 entrées et 5 dossiers lourds de 2 000 entrées et 1 500 liens
 * sur 10 ans), {@link #demo} (le patient DEMO), {@link #heavy} (un dossier lourd paramétrable).
 */
public final class Scenario {

  private Scenario() {}

  /** Préfixe des patients générés : sert à l'idempotence. */
  public static final String SEED_PREFIX = "SEED · ";

  /** Préfixe du patient de démonstration (G, Q22). */
  public static final String DEMO_PREFIX = "DEMO · ";

  /** Graine par défaut. */
  public static final long DEFAULT_SEED = 20261101L;

  /** Mot fréquent du corpus, pour la recherche. */
  public static final String FREQUENT_WORD = Corpus.FREQUENT_WORD;

  /** Libellés de tags employés par un plan (à créer avant les patients). */
  public static List<String> allTags(Plan plan) {
    Set<String> tags = new java.util.TreeSet<>();
    for (PatientPlan p : plan.patients()) {
      tags.addAll(p.tags());
    }
    return List.copyOf(tags);
  }

  // ---- fabriques

  public static Plan small(long seed) {
    return small(seed, today());
  }

  public static Plan small(long seed, Instant today) {
    List<PatientPlan> patients = new ArrayList<>();
    NamePool names = new NamePool(new Random(seed), SEED_PREFIX);
    for (int i = 0; i < 5; i++) {
      Practitioner who = i < 2 ? Practitioner.CLAIRE : Practitioner.KARIM;
      patients.add(ordinary(seed, i, who, names, today));
    }
    return new Plan(List.copyOf(patients));
  }

  public static Plan medium(long seed) {
    return medium(seed, today());
  }

  public static Plan medium(long seed, Instant today) {
    List<PatientPlan> patients = new ArrayList<>();
    NamePool names = new NamePool(new Random(seed), SEED_PREFIX);
    // dossiers lourds d'abord : ce sont les plus longs à écrire, ils partent en tête de file
    Practitioner[] heavyOwners = {
      Practitioner.KARIM,
      Practitioner.CLAIRE,
      Practitioner.KARIM,
      Practitioner.KARIM,
      Practitioner.CLAIRE
    };
    for (int i = 0; i < heavyOwners.length; i++) {
      patients.add(heavyPatient(seed, 1000 + i, 2000, 1500, heavyOwners[i], names, today));
    }
    for (int i = 0; i < 100; i++) {
      Practitioner who = i % 5 < 2 ? Practitioner.CLAIRE : Practitioner.KARIM;
      patients.add(ordinary(seed, i, who, names, today));
    }
    return new Plan(List.copyOf(patients));
  }

  public static Plan demo() {
    return demo(today());
  }

  public static Plan demo(Instant today) {
    return new Plan(List.of(DemoStory.build(today)));
  }

  public static Plan heavy(long seed, int entries, int links) {
    return heavy(seed, entries, links, today());
  }

  /** Un seul patient lourd (10 ans d'historique, suivi par le psychiatre). */
  public static Plan heavy(long seed, int entries, int links, Instant today) {
    NamePool names = new NamePool(new Random(seed), SEED_PREFIX);
    return new Plan(
        List.of(heavyPatient(seed, 1000, entries, links, Practitioner.KARIM, names, today)));
  }

  /** Mélange (graine, indice) : des graines voisines ne doivent pas donner des tirages voisins. */
  private static long mix(long seed, int index) {
    long z = seed + 0x9E3779B97F4A7C15L * (index + 1L);
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }

  /** Aujourd'hui à minuit UTC : les séances générées sont toutes strictement passées. */
  static Instant today() {
    return Instant.now().truncatedTo(ChronoUnit.DAYS);
  }

  private static PatientPlan ordinary(
      long seed, int index, Practitioner who, NamePool names, Instant today) {
    Random rnd = new Random(mix(seed, index));
    int years = 1 + rnd.nextInt(4);
    int entries = 180 + rnd.nextInt(41);
    boolean medicated = rnd.nextDouble() < (who == Practitioner.KARIM ? 0.85 : 0.1);
    return build(
        rnd, names, who, entries, (int) Math.round(entries * 0.75), years, medicated, 3, today);
  }

  private static PatientPlan heavyPatient(
      long seed,
      int index,
      int entries,
      int links,
      Practitioner who,
      NamePool names,
      Instant today) {
    Random rnd = new Random(mix(seed, index));
    boolean medicated = who == Practitioner.KARIM || rnd.nextBoolean();
    return build(rnd, names, who, entries, links, 10, medicated, 4, today);
  }

  private static PatientPlan build(
      Random rnd,
      NamePool names,
      Practitioner who,
      int entries,
      int links,
      int years,
      boolean medicated,
      int tagCount,
      Instant today) {
    NamePool.Identity id = names.next();
    int startYear = today.atZone(ZoneOffset.UTC).getYear() - years;
    int birthYear = 1958 + rnd.nextInt(Math.min(2006, startYear - 18) - 1958 + 1);
    LocalDate birth = LocalDate.of(birthYear, 1 + rnd.nextInt(12), 1 + rnd.nextInt(28));
    List<String> all = new ArrayList<>();
    for (Corpus.Theme t : Corpus.THEMES) {
      all.add(t.tag());
    }
    Collections.shuffle(all, rnd);
    List<String> tags = new ArrayList<>(all.subList(0, tagCount));
    Collections.sort(tags);
    PatientGenerator.Config cfg =
        new PatientGenerator.Config(
            id.firstName(),
            id.lastName(),
            id.displayName(),
            id.pronoun(),
            birth,
            who,
            entries,
            links,
            years,
            medicated,
            List.copyOf(tags));
    return new PatientGenerator(rnd, today, cfg).generate();
  }

  // ---- noms uniques

  private static final class NamePool {
    record Identity(String firstName, String lastName, String pronoun, String displayName) {}

    private final Random rnd;
    private final String prefix;
    private final Set<String> used = new HashSet<>();

    NamePool(Random rnd, String prefix) {
      this.rnd = rnd;
      this.prefix = prefix;
    }

    Identity next() {
      for (int attempt = 0; attempt < 10_000; attempt++) {
        Corpus.FirstName first = Corpus.FIRST_NAMES[rnd.nextInt(Corpus.FIRST_NAMES.length)];
        String last = Corpus.LAST_NAMES[rnd.nextInt(Corpus.LAST_NAMES.length)];
        String display = prefix + first.name() + " " + last.charAt(0) + ".";
        if (used.add(display)) {
          return new Identity(first.name(), last, first.pronoun(), display);
        }
      }
      throw new IllegalStateException("Plus de nom fictif disponible");
    }
  }

  // ---- statistiques

  /** Résumé chiffré d'un plan. */
  public record Stats(
      int patients,
      int sessions,
      int entries,
      Map<String, Integer> entriesByType,
      int links,
      int imprecise,
      int privateEntries,
      int patches,
      int maxDegree) {

    public String describe() {
      return "%d patient(s), %d séances, %d entrées %s, %d liens (pivot max : %d), %d dates imprécises (%.1f %%), %d entrées privées (%.1f %%), %d corrections"
          .formatted(
              patients,
              sessions,
              entries,
              entriesByType,
              links,
              maxDegree,
              imprecise,
              entries == 0 ? 0.0 : 100.0 * imprecise / entries,
              privateEntries,
              entries == 0 ? 0.0 : 100.0 * privateEntries / entries,
              patches);
    }
  }

  public static Stats stats(Plan plan) {
    int sessions = 0;
    int entries = 0;
    int links = 0;
    int imprecise = 0;
    int privates = 0;
    int patches = 0;
    int maxDegree = 0;
    Map<String, Integer> byType = new LinkedHashMap<>();
    for (String t : List.of("note", "event", "situation", "feeling", "medication")) {
      byType.put(t, 0);
    }
    for (PatientPlan p : plan.patients()) {
      Map<String, Integer> degree = new HashMap<>();
      for (Step step : p.steps()) {
        switch (step) {
          case StartSession s -> sessions++;
          case CloseSession c -> {}
          case PatchEntry pe -> patches++;
          case CreateEntry ce -> {
            entries++;
            byType.merge((String) ce.body().get("type"), 1, Integer::sum);
            if (!"exact".equals(precisionOf(ce.body()))) {
              imprecise++;
            }
            if ("private".equals(ce.body().get("visibility"))) {
              privates++;
            }
            for (LinkPlan l : ce.links()) {
              links++;
              degree.merge(ce.ref(), 1, Integer::sum);
              degree.merge(l.targetRef(), 1, Integer::sum);
            }
          }
        }
      }
      for (int d : degree.values()) {
        maxDegree = Math.max(maxDegree, d);
      }
    }
    return new Stats(
        plan.patients().size(),
        sessions,
        entries,
        byType,
        links,
        imprecise,
        privates,
        patches,
        maxDegree);
  }

  @SuppressWarnings("unchecked")
  private static String precisionOf(Map<String, Object> body) {
    Object occurred = body.get("occurred");
    if (occurred instanceof Map<?, ?> o) {
      Object p = ((Map<String, Object>) o).get("precision");
      return p == null ? "exact" : p.toString();
    }
    return "exact";
  }

  /** Clé de la situation du dossier qui porte le plus de liens (le pivot du graphe). */
  public static String mostLinkedSituation(PatientPlan patient) {
    Map<String, Integer> degree = new HashMap<>();
    Set<String> situations = new HashSet<>();
    for (Step step : patient.steps()) {
      if (step instanceof CreateEntry ce) {
        if ("situation".equals(ce.body().get("type"))) {
          situations.add(ce.ref());
        }
        for (LinkPlan l : ce.links()) {
          degree.merge(ce.ref(), 1, Integer::sum);
          degree.merge(l.targetRef(), 1, Integer::sum);
        }
      }
    }
    String best = null;
    int max = -1;
    for (String ref : situations) {
      int d = degree.getOrDefault(ref, 0);
      if (d > max) {
        max = d;
        best = ref;
      }
    }
    return best;
  }

  // ---- contrôle de cohérence

  private static final Set<String> EMOTIONS =
      Set.of(
          "serenity",
          "joy",
          "ecstasy",
          "acceptance",
          "trust",
          "admiration",
          "calm",
          "apprehension",
          "fear",
          "terror",
          "anxiety",
          "distraction",
          "surprise",
          "amazement",
          "pensiveness",
          "sadness",
          "grief",
          "guilt",
          "emptiness",
          "boredom",
          "disgust",
          "loathing",
          "shame",
          "annoyance",
          "anger",
          "rage",
          "interest",
          "anticipation",
          "vigilance");

  private static final Set<String> PRECISIONS =
      Set.of("exact", "day", "month", "year", "period", "life_phase");

  private static final Set<String> FORBIDDEN_KEYS =
      Set.of("authorType", "submissionId", "sessionId", "links");

  /**
   * Vérifie qu'un plan respecte E (E5.5) et les règles de l'API : séance pour toute entrée, pas
   * d'authorType, liens conformes à la matrice E5.3 sans doublon, cibles déjà créées, au plus 20
   * liens, entrées privées limitées aux notes, ressentis et événements, précisions cohérentes.
   *
   * @return la liste des violations, vide si le plan est sain
   */
  @SuppressWarnings("unchecked")
  public static List<String> validate(Plan plan) {
    List<String> problems = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (PatientPlan p : plan.patients()) {
      String who = p.displayName();
      if (!names.add(who)) {
        problems.add(who + " : nom en double dans le plan");
      }
      if (!who.startsWith(SEED_PREFIX) && !who.startsWith(DEMO_PREFIX)) {
        problems.add(who + " : préfixe SEED · / DEMO · manquant");
      }
      if (p.birthDate() == null) {
        problems.add(who + " : date de naissance manquante");
      }
      Map<String, String> kinds = new HashMap<>();
      Set<String> openSessions = new HashSet<>();
      for (Step step : p.steps()) {
        switch (step) {
          case StartSession s -> {
            if (!openSessions.isEmpty()) {
              problems.add(
                  who + " : séance " + s.ref() + " démarrée avant la clôture de la précédente");
            }
            kinds.put(s.ref(), LinkRules.SESSION);
            openSessions.add(s.ref());
          }
          case CloseSession c -> {
            if (!openSessions.remove(c.sessionRef())) {
              problems.add(who + " : clôture d'une séance non ouverte " + c.sessionRef());
            }
          }
          case PatchEntry pe -> {
            if (!kinds.containsKey(pe.ref())) {
              problems.add(who + " : PATCH d'une entrée inconnue " + pe.ref());
            }
          }
          case CreateEntry ce -> {
            Map<String, Object> body = ce.body();
            String type = (String) body.get("type");
            if (type == null || type.equals("session") || !kinds.containsKey(ce.sessionRef())) {
              problems.add(
                  who
                      + " : "
                      + ce.ref()
                      + " sans type valide ou sans séance ("
                      + ce.sessionRef()
                      + ")");
              continue;
            }
            if (!LinkRules.SESSION.equals(kinds.get(ce.sessionRef()))) {
              problems.add(who + " : " + ce.ref() + " rattachée à autre chose qu'une séance");
            }
            for (String key : FORBIDDEN_KEYS) {
              if (body.containsKey(key)) {
                problems.add(who + " : " + ce.ref() + " porte le champ interdit " + key);
              }
            }
            Object vis = body.get("visibility");
            if ("private".equals(vis) && !Set.of("note", "feeling", "event").contains(type)) {
              problems.add(who + " : " + ce.ref() + " privée alors que de type " + type);
            }
            if (vis != null && !"private".equals(vis) && !"clinical".equals(vis)) {
              problems.add(who + " : " + ce.ref() + " visibilité inconnue " + vis);
            }
            Object tags = body.get("tags");
            if (tags instanceof List<?> list) {
              for (Object t : list) {
                if (!p.tags().contains(t)) {
                  problems.add(who + " : " + ce.ref() + " tag hors des tags du patient : " + t);
                }
              }
            }
            checkContent(who, ce, type, problems);
            if (ce.links().size() > PatientBuilder.MAX_LINKS) {
              problems.add(who + " : " + ce.ref() + " a plus de 20 liens");
            }
            Set<String> targets = new HashSet<>();
            for (LinkPlan l : ce.links()) {
              String target = kinds.get(l.targetRef());
              if (target == null) {
                problems.add(
                    who + " : " + ce.ref() + " lie une cible pas encore créée " + l.targetRef());
                continue;
              }
              if (!targets.add(l.targetRef())) {
                problems.add(who + " : " + ce.ref() + " lie deux fois " + l.targetRef());
              }
              boolean outgoing = "outgoing".equals(l.direction());
              if (!outgoing && !"incoming".equals(l.direction())) {
                problems.add(who + " : " + ce.ref() + " direction inconnue " + l.direction());
              }
              boolean ok =
                  outgoing
                      ? LinkRules.allowed(l.linkType(), type, target)
                      : LinkRules.allowed(l.linkType(), target, type);
              if (!ok) {
                problems.add(
                    who
                        + " : "
                        + ce.ref()
                        + " lien "
                        + l.linkType()
                        + " "
                        + l.direction()
                        + " "
                        + type
                        + "/"
                        + target
                        + " hors matrice");
              }
            }
            kinds.put(ce.ref(), type);
          }
        }
      }
      if (!openSessions.isEmpty()) {
        problems.add(who + " : séance restée ouverte");
      }
    }
    return problems;
  }

  @SuppressWarnings("unchecked")
  private static void checkContent(String who, CreateEntry ce, String type, List<String> problems) {
    Map<String, Object> body = ce.body();
    String ref = who + " : " + ce.ref();
    switch (type) {
      case "note" -> require(body, "body", ref, problems);
      case "event", "situation" -> require(body, "title", ref, problems);
      case "feeling" -> {
        Object f = body.get("feeling");
        if (!(f instanceof Map<?, ?> fm)) {
          problems.add(ref + " : ressenti sans détail");
        } else {
          Object code = fm.get("emotionCode");
          Object intensity = fm.get("intensity");
          if (!EMOTIONS.contains(code)) {
            problems.add(ref + " : émotion inconnue " + code);
          }
          if (!(intensity instanceof Integer i) || i < 0 || i > 10) {
            problems.add(ref + " : intensité hors 0..10 " + intensity);
          }
        }
      }
      case "medication" -> {
        Object m = body.get("medication");
        if (!(m instanceof Map<?, ?> mm) || mm.get("molecule") == null) {
          problems.add(ref + " : médicament sans molécule");
        }
      }
      default -> problems.add(ref + " : type inconnu " + type);
    }
    Object occurred = body.get("occurred");
    if (occurred == null) {
      problems.add(ref + " : occurred manquant");
      return;
    }
    Map<String, Object> o = (Map<String, Object>) occurred;
    String precision = o.get("precision") == null ? "exact" : o.get("precision").toString();
    if (!PRECISIONS.contains(precision)) {
      problems.add(ref + " : précision inconnue " + precision);
    }
    if (o.get("from") == null && !"life_phase".equals(precision)) {
      problems.add(ref + " : occurred.from manquant");
    }
    if ("period".equals(precision)
        && (o.get("label") == null || (o.get("to") == null && !"situation".equals(type)))) {
      problems.add(ref + " : période sans libellé ou sans fin");
    }
    if ("life_phase".equals(precision) && !"enfance".equals(o.get("label"))) {
      problems.add(ref + " : seule la phase « enfance » est connue");
    }
    if (o.get("from") != null && o.get("to") != null) {
      Instant from = Instant.parse(o.get("from").toString());
      Instant to = Instant.parse(o.get("to").toString());
      if (to.isBefore(from)) {
        problems.add(ref + " : fin avant le début");
      }
    }
  }

  private static void require(
      Map<String, Object> body, String key, String ref, List<String> problems) {
    Object v = body.get(key);
    if (!(v instanceof String s) || s.isBlank()) {
      problems.add(ref + " : champ " + key + " manquant");
    }
  }
}
