package com.psymate.patients.api;

import java.util.UUID;

/** Garde de propriété (ADR-09) : à appeler avant toute lecture ou écriture sur un patient. */
public interface PatientOwnershipGuard {

  /**
   * Vérifie que le patient appartient au praticien courant et à son cabinet.
   *
   * @throws org.springframework.web.server.ResponseStatusException 403 si ce n'est pas le cas, 404
   *     si le patient n'existe pas
   */
  void requireOwned(UUID patientId);
}
