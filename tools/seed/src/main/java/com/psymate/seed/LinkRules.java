package com.psymate.seed;

import java.util.Map;
import java.util.Set;

/**
 * Matrice des types de liens (E5.3), recopiée telle quelle : sert à ne générer que des liens que
 * l'API acceptera, et à contrôler un plan ({@link Scenario#validate}).
 *
 * <p>Les types d'entrées sont ceux de l'API : note, event, situation, feeling, medication, session.
 */
final class LinkRules {

  private LinkRules() {}

  static final String NOTE = "note";
  static final String EVENT = "event";
  static final String SITUATION = "situation";
  static final String FEELING = "feeling";
  static final String MEDICATION = "medication";
  static final String SESSION = "session";

  private record Rule(Set<String> sources, Set<String> targets) {}

  private static final Map<String, Rule> MATRIX =
      Map.of(
          "triggers",
          new Rule(Set.of(EVENT, SITUATION, SESSION), Set.of(FEELING, SITUATION, EVENT)),
          "caused_by",
          new Rule(Set.of(FEELING, SITUATION, EVENT), Set.of(EVENT, SITUATION)),
          "worsens",
          new Rule(Set.of(SITUATION, EVENT, MEDICATION), Set.of(FEELING, SITUATION)),
          "relieves",
          new Rule(Set.of(EVENT, SITUATION, MEDICATION, SESSION), Set.of(FEELING, SITUATION)),
          "co_occurs",
          new Rule(
              Set.of(NOTE, EVENT, SITUATION, FEELING, MEDICATION),
              Set.of(NOTE, EVENT, SITUATION, FEELING, MEDICATION)),
          "follows",
          new Rule(
              Set.of(NOTE, EVENT, SITUATION, FEELING), Set.of(NOTE, EVENT, SITUATION, FEELING)),
          "part_of",
          new Rule(Set.of(EVENT, FEELING, NOTE), Set.of(SITUATION, SESSION)),
          "treats",
          new Rule(Set.of(MEDICATION), Set.of(SITUATION, FEELING)));

  /**
   * Vrai si un lien {@code type} peut aller d'une entrée de type {@code source} vers {@code
   * target}.
   */
  static boolean allowed(String type, String source, String target) {
    Rule rule = MATRIX.get(type);
    return rule != null && rule.sources().contains(source) && rule.targets().contains(target);
  }

  static boolean known(String type) {
    return MATRIX.containsKey(type);
  }
}
