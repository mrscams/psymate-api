package com.psymate.clinical.export.repository;

import java.util.UUID;

/**
 * SQL natif de la demande d'export (G Q38) : le verrou de transaction du praticien, que JPA
 * n'exprime pas. Fragment Spring Data de {@link ExportJobJpaRepository}, sur l'{@code
 * EntityManager} de la transaction courante.
 */
interface ExportJobNativeQueries {

  /**
   * {@code pg_advisory_xact_lock} sur la clé {@code export:<praticien>}, tenu jusqu'au commit :
   * sérialise les demandes d'un même praticien.
   */
  void lockPractitioner(UUID practitionerId);
}
