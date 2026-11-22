package com.psymate.agenda.model;

/**
 * Origine d'un rendez-vous (colonne {@code appointment.origin}, V15, G Q74) : {@link #PRACTITIONER}
 * pour un rendez-vous posé par le praticien (toujours le cas de {@code POST /api/appointments}),
 * {@link #ONLINE} pour un rendez-vous né d'une demande en ligne vérifiée (M14-06). Persistée par
 * {@link AppointmentOriginConverter} avec la valeur SQL exacte de la contrainte CHECK ({@code
 * practitioner}, {@code online}), jamais par ordinal ; exposée telle quelle par l'API. M12 (Google)
 * ajoutera une valeur ici plutôt qu'une colonne {@code source}.
 */
public enum AppointmentOrigin {
  PRACTITIONER("practitioner"),
  ONLINE("online");

  private final String value;

  AppointmentOrigin(String value) {
    this.value = value;
  }

  /** Valeur SQL et JSON : {@code practitioner} ou {@code online}. */
  public String value() {
    return value;
  }

  /** Origine d'une valeur SQL ; {@link IllegalArgumentException} si elle est inconnue. */
  public static AppointmentOrigin of(String value) {
    for (AppointmentOrigin o : values()) {
      if (o.value.equals(value)) {
        return o;
      }
    }
    throw new IllegalArgumentException("unknown appointment origin: " + value);
  }
}
