package com.psymate.patients.model;

/**
 * Rubrique d'une information du Profil 360 (colonne {@code patient_profile_item.category}, G Q64).
 * Liste fermée portée par le code et par la contrainte CHECK : les constantes portent la valeur SQL
 * exacte, persistées en texte par {@code EnumType.STRING}, jamais par ordinal. L'ordre de
 * déclaration est l'ordre d'affichage de l'onglet (J3).
 */
public enum PatientProfileCategory {
  /** Histoire et souvenirs : seule rubrique qui admet une période (G Q66). */
  history,
  /** Famille et entourage. */
  family,
  /** Santé et antécédents. */
  health,
  /** Valeurs et projets. */
  values_goals;

  /** Rubrique d'une valeur reçue, ou null si elle est inconnue (ou null). */
  public static PatientProfileCategory parse(String value) {
    if (value == null) {
      return null;
    }
    for (PatientProfileCategory c : values()) {
      if (c.name().equals(value)) {
        return c;
      }
    }
    return null;
  }

  /** Vrai pour la seule rubrique datée, {@link #history}. */
  public boolean hasPeriod() {
    return this == history;
  }
}
