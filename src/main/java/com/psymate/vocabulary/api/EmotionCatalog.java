package com.psymate.vocabulary.api;

import java.util.List;
import java.util.Optional;

/** Façade du catalogue d'émotions : globales et personnalisées du praticien courant, actives. */
public interface EmotionCatalog {

  /**
   * Les émotions globales actives plus les custom actives du praticien courant, jamais d'autres.
   */
  List<EmotionView> activeEmotions();

  /** Cherche une émotion active (globale ou custom du praticien courant) par libellé normalisé. */
  Optional<EmotionView> findActiveByLabel(String label);

  /**
   * Valence et activation d'une émotion active (globale ou custom du praticien courant), vide si le
   * code est inconnu, inactif ou appartient à un autre praticien.
   */
  Optional<EmotionAffect> affectOf(String code);
}
