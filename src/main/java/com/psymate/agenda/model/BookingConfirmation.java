package com.psymate.agenda.model;

/**
 * Mode de confirmation d'une demande en ligne (préférence {@code booking.confirmation}, G Q74) :
 * {@code manual} (défaut), le praticien confirme ou refuse chaque demande ; {@code auto}, la
 * demande vérifiée devient aussitôt un rendez-vous planifié. Les constantes portent la valeur
 * exacte de la préférence et du contrat.
 */
public enum BookingConfirmation {
  manual,
  auto
}
