package com.psymate.agenda.model;

/**
 * Modalité d'un rendez-vous (colonne {@code appointment.modality}, I2). Les constantes portent la
 * valeur SQL exacte de la contrainte CHECK : persistées en texte par {@code EnumType.STRING},
 * jamais par ordinal.
 */
public enum AppointmentModality {
  in_person,
  video,
  phone
}
