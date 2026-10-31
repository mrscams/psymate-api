package com.psymate.vocabulary.api;

/** Émotion exposée aux autres modules : globale (produit) ou personnalisée du praticien courant. */
public record EmotionView(
    String code,
    String scope,
    String family,
    Integer intensityLevel,
    String labelFr,
    String labelEn,
    boolean active) {}
