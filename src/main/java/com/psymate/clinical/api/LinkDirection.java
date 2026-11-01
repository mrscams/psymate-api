package com.psymate.clinical.api;

/**
 * Sens d'un lien demandé à la création d'une entrée, vu depuis la nouvelle entrée : {@code
 * OUTGOING} (défaut) fait de la nouvelle entrée la source ; {@code INCOMING} en fait la cible, la
 * cible demandée devenant la source (« l'événement déclenche le ressenti »).
 */
public enum LinkDirection {
  OUTGOING,
  INCOMING
}
