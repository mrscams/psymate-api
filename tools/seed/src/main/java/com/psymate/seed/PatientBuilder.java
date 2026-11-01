package com.psymate.seed;

import com.psymate.seed.Plan.CloseSession;
import com.psymate.seed.Plan.CreateEntry;
import com.psymate.seed.Plan.LinkPlan;
import com.psymate.seed.Plan.PatchEntry;
import com.psymate.seed.Plan.StartSession;
import com.psymate.seed.Plan.Step;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Construit les étapes d'un dossier en vérifiant à chaque ajout ce que l'API vérifiera : cibles de
 * liens déjà créées, matrice E5.3, pas de doublon, au plus 20 liens par entrée. Utilisé par le
 * générateur aléatoire et par {@link DemoStory} ; une erreur de scénario est donc détectée à la
 * génération, pas pendant l'écriture.
 */
final class PatientBuilder {

  static final int MAX_LINKS = 20;

  private final List<Step> steps = new ArrayList<>();
  private final Map<String, String> kinds = new HashMap<>();
  private int counter;
  private int linkCount;
  private String openSession;

  List<Step> steps() {
    return steps;
  }

  int linkCount() {
    return linkCount;
  }

  String kindOf(String ref) {
    return kinds.get(ref);
  }

  boolean exists(String ref) {
    return kinds.containsKey(ref);
  }

  // ---- séances

  /** Démarre une séance ; la précédente doit être clôturée. */
  String startSession(Instant from, String modality) {
    if (openSession != null) {
      throw new IllegalStateException("La séance " + openSession + " n'est pas clôturée");
    }
    String ref = "x" + (++counter);
    kinds.put(ref, LinkRules.SESSION);
    steps.add(new StartSession(ref, from.truncatedTo(ChronoUnit.SECONDS), modality));
    openSession = ref;
    return ref;
  }

  void closeSession(String ref, int durationMin, String summary) {
    if (!ref.equals(openSession)) {
      throw new IllegalStateException("La séance " + ref + " n'est pas la séance ouverte");
    }
    steps.add(new CloseSession(ref, durationMin, summary));
    openSession = null;
  }

  // ---- entrées

  /**
   * Ajoute une entrée. {@code body} ne contient ni type, ni sessionId, ni links.
   *
   * @return la clé de l'entrée
   */
  String entry(String sessionRef, String kind, Map<String, Object> body, List<LinkPlan> links) {
    if (!LinkRules.SESSION.equals(kinds.get(sessionRef))) {
      throw new IllegalStateException("sessionRef inconnue : " + sessionRef);
    }
    checkLinks(kind, links);
    Map<String, Object> full = new LinkedHashMap<>();
    full.put("type", kind);
    full.putAll(body);
    String ref = "e" + (++counter);
    kinds.put(ref, kind);
    steps.add(new CreateEntry(ref, sessionRef, full, List.copyOf(links)));
    linkCount += links.size();
    return ref;
  }

  void patch(String ref, Map<String, Object> body) {
    if (!kinds.containsKey(ref)) {
      throw new IllegalStateException("PATCH d'une entrée inconnue : " + ref);
    }
    steps.add(new PatchEntry(ref, body));
  }

  /** Teste un lien sans l'ajouter : cible connue, matrice, pas déjà dans {@code existing}. */
  boolean linkOk(String newKind, List<LinkPlan> existing, LinkPlan link) {
    String target = kinds.get(link.targetRef());
    if (target == null || existing.size() >= MAX_LINKS) {
      return false;
    }
    for (LinkPlan other : existing) {
      if (other.targetRef().equals(link.targetRef())) {
        return false; // une seule relation par couple d'entrées
      }
    }
    boolean outgoing = "outgoing".equals(link.direction());
    return outgoing
        ? LinkRules.allowed(link.linkType(), newKind, target)
        : LinkRules.allowed(link.linkType(), target, newKind);
  }

  private void checkLinks(String kind, List<LinkPlan> links) {
    List<LinkPlan> seen = new ArrayList<>();
    Set<String> pairs = new HashSet<>();
    for (LinkPlan l : links) {
      if (!pairs.add(l.targetRef()) || !linkOk(kind, seen, l)) {
        throw new IllegalStateException(
            "Lien refusé par la matrice E5.3 ou en doublon : " + kind + " " + l);
      }
      seen.add(l);
    }
  }
}
