package com.psymate.identity.verification;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Règles de complétude du dossier professionnel, à modifier ici et nulle part ailleurs.
 *
 * <p>G Q14 : toutes les professions exigent un diplôme ET une pièce d'identité. L'attestation
 * ADELI/RPPS (type {@code registration}) est recommandée pour les psychologues et les psychiatres,
 * une certification pour les coachs ; une pièce recommandée n'est jamais bloquante.
 */
final class CompletenessRules {

  /** Types obligatoires pour toutes les professions (G Q14). */
  static final List<String> REQUIRED = List.of("diploma", "identity");

  /** Types recommandés, non bloquants, par profession (G Q14). */
  private static final Map<String, List<String>> RECOMMENDED =
      Map.of(
          "psychologist", List.of("registration"),
          "psychiatrist", List.of("registration"),
          "coach", List.of("certification"));

  private CompletenessRules() {}

  /** Types obligatoires absents du dossier, dans l'ordre de {@link #REQUIRED}. */
  static List<String> missing(String profession, Collection<String> presentTypes) {
    return REQUIRED.stream().filter(t -> !presentTypes.contains(t)).toList();
  }

  /** Types recommandés absents du dossier (affichés en encart « conseillé »). */
  static List<String> recommended(String profession, Collection<String> presentTypes) {
    return RECOMMENDED.getOrDefault(profession, List.of()).stream()
        .filter(t -> !presentTypes.contains(t))
        .toList();
  }
}
