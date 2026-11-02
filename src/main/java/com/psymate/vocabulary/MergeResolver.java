package com.psymate.vocabulary;

import com.psymate.vocabulary.api.EmotionResolution;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Règles pures de résolution des fusions (testables sans base), pour {@link MergeService}. */
final class MergeResolver {

  /** Une fusion d'émotions réduite à ce qui compte pour la résolution. */
  record EmotionEdge(String source, String target, boolean undone) {}

  private MergeResolver() {}

  /**
   * Résolution de chaque source de fusion dont l'historique contient {@code code}. La cible
   * courante suit les fusions actives (au plus une par source : une source fusionnée est inactive
   * et ne peut pas l'être de nouveau avant l'annulation) ; l'historique suit toutes les fusions. Un
   * cycle, impossible en pratique, arrête le parcours au lieu de boucler.
   */
  static List<EmotionResolution> emotions(List<EmotionEdge> merges, String code) {
    Map<String, String> active = new HashMap<>();
    Map<String, Set<String>> all = new HashMap<>();
    for (EmotionEdge m : merges) {
      if (!m.undone()) {
        active.put(m.source(), m.target());
      }
      all.computeIfAbsent(m.source(), k -> new LinkedHashSet<>()).add(m.target());
    }
    List<EmotionResolution> out = new ArrayList<>();
    for (String source : new TreeSet<>(all.keySet())) {
      Set<String> history = reachable(source, all);
      if (history.contains(code)) {
        out.add(new EmotionResolution(source, follow(source, active), Set.copyOf(history)));
      }
    }
    return out;
  }

  /** Pour chaque tag archivé par une fusion, le bout de sa chaîne {@code merged_into}. */
  static Map<UUID, UUID> tags(Map<UUID, UUID> mergedInto) {
    Map<UUID, UUID> out = new HashMap<>();
    mergedInto.keySet().forEach(source -> out.put(source, follow(source, mergedInto)));
    return out;
  }

  private static <T> T follow(T start, Map<T, T> next) {
    Set<T> seen = new HashSet<>();
    T current = start;
    while (next.containsKey(current) && seen.add(current)) {
      current = next.get(current);
    }
    return current;
  }

  private static Set<String> reachable(String start, Map<String, Set<String>> edges) {
    Set<String> seen = new LinkedHashSet<>();
    Deque<String> todo = new ArrayDeque<>(List.of(start));
    while (!todo.isEmpty()) {
      String c = todo.pop();
      if (seen.add(c)) {
        todo.addAll(edges.getOrDefault(c, Set.of()));
      }
    }
    return seen;
  }
}
