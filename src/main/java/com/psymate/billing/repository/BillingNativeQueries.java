package com.psymate.billing.repository;

import java.util.UUID;

/**
 * SQL natif de la facturation (G Q38 : fragment Spring Data sur l'{@code EntityManager} du module,
 * une seule unité de travail). Même règle que l'agenda : le verrou consultatif transactionnel ne
 * protège qu'en isolation READ COMMITTED (défaut de PostgreSQL) et l'appelant relit après le verrou
 * ce qu'il protège.
 */
public interface BillingNativeQueries {

  /**
   * Verrou consultatif transactionnel des prestations d'un praticien ({@code
   * pg_advisory_xact_lock}) : les créations, changements de défaut et archivages d'un même
   * praticien se suivent une à une, si bien que la limite de 50 et la prestation par défaut unique
   * tiennent en parallèle. Exige une transaction en cours ; libéré à sa fin.
   */
  void lockServices(UUID practitionerId);
}
