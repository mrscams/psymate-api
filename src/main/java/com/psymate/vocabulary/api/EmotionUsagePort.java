package com.psymate.vocabulary.api;

/**
 * Comptage des ressentis par émotion, implémenté par le module propriétaire des ressentis
 * (clinical), sur le modèle de {@code clinical.api.EntryLinksPort} : vocabulary en a besoin pour
 * {@code affectedCount} d'une fusion, sans lire une table d'un autre module (G Q34, M9-03).
 */
public interface EmotionUsagePort {

  /** Nombre de ressentis dont l'émotion courante est {@code emotionCode}. */
  int feelingsWith(String emotionCode);
}
