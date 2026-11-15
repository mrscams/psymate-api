package com.psymate.agenda.model;

/**
 * Statut d'un rendez-vous (G Q52) : {@code planned} par défaut, {@code tentative} (case « À
 * confirmer »), {@code cancelled} (jamais de suppression, G Q53), {@code done} (séance rattachée,
 * M11-08 ; aucune transition manuelle en M11). Les constantes portent la valeur SQL exacte de la
 * contrainte CHECK, persistée en texte.
 */
public enum AppointmentStatus {
  planned,
  tentative,
  cancelled,
  done;

  /** Un rendez-vous actif compte dans les chevauchements et peut encore être modifié (G Q54). */
  public boolean isActive() {
    return this == planned || this == tentative;
  }
}
