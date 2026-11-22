package com.psymate.agenda.repository;

import java.util.UUID;

/**
 * SQL natif de la prise de rendez-vous en ligne (G Q38 : fragment Spring Data sur l'{@code
 * EntityManager} du module, une seule unité de travail).
 */
public interface BookingNativeQueries {

  /**
   * Verrou consultatif transactionnel du praticien ({@code pg_advisory_xact_lock}, G Q77) : la
   * vérification d'une demande et l'annulation par le demandeur se suivent une à une pour un même
   * praticien. Exige une transaction en cours ; libéré à sa fin (validation ou annulation). Toute
   * lecture faite après l'appel voit ce que la transaction précédente a validé.
   */
  void lockPractitioner(UUID practitionerId);
}
