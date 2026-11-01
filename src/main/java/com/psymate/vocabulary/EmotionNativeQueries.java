package com.psymate.vocabulary;

/**
 * Réécritures de entry_feeling (table de clinical, jamais mappée ici) lors d'une fusion ou d'une
 * annulation : SQL natif conservé tel quel, avec son {@code coalesce} et ses garde-fous sur
 * l'existence de la table.
 */
interface EmotionNativeQueries {

  /**
   * emotion_code passe à la cible, original_emotion_code garde la source. Sans effet tant que la
   * table n'existe pas. Retourne le nombre de lignes touchées.
   */
  int rewriteFeelings(String source, String target);

  /** Défait la réécriture : remet emotion_code = source pour les lignes issues de la fusion. */
  int restoreFeelings(String source, String target);
}
