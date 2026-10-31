package com.psymate.vocabulary.api;

/**
 * Valence (-2..2) et activation (0..4) d'une émotion utilisable par le praticien courant ; chaque
 * valeur est NULL tant que l'émotion n'est pas renseignée (V1). Non exposé par REST.
 */
public record EmotionAffect(String code, Integer valence, Integer arousal) {}
