package com.psymate.patients.persistence;

import java.util.List;
import java.util.UUID;

/**
 * Les deux seules requêtes natives du module : elles utilisent {@code f_unaccent} (index trigramme
 * {@code ix_patient_name_trgm}, tri insensible aux accents) que JPQL ne reproduit pas.
 */
public interface PatientNativeQueries {

  /**
   * Patients du praticien, filtrés et triés. {@code orderBy} est une des trois valeurs fixées par
   * PatientService (jamais une saisie utilisateur) ; {@code pattern} et {@code tagId} sont
   * facultatifs.
   */
  List<Patient> list(
      UUID tenant,
      UUID owner,
      String pattern,
      boolean archived,
      UUID tagId,
      String orderBy,
      int limit,
      int offset);

  /** Tous les patients du praticien, archivés compris, par nom affiché (export du compte). */
  List<Patient> findOwnedByOrdered(UUID ownerId);
}
