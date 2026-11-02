package com.psymate.patients.api;

import java.util.UUID;

/** Garde de propriété (ADR-09) : à appeler avant toute lecture ou écriture sur un patient. */
public interface PatientOwnershipGuard {

  /**
   * Vérifie que le patient appartient au praticien courant et à son cabinet.
   *
   * @throws PatientAccessException 403 PATIENT_FORBIDDEN si ce n'est pas le cas, 404
   *     PATIENT_NOT_FOUND si le patient n'existe pas (et une {@code ProblemException} 401
   *     UNAUTHENTICATED sans praticien authentifié, cas que la chaîne de sécurité écarte avant)
   */
  void requireOwned(UUID patientId);
}
