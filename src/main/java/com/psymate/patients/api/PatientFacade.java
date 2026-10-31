package com.psymate.patients.api;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Lecture minimale d'un patient pour les autres modules. */
public interface PatientFacade {

  /** Le patient existe (archivé ou non). */
  boolean exists(UUID patientId);

  /** Le patient appartient à ce praticien ET, s'il y a une session, au cabinet de celle-ci. */
  boolean ownedBy(UUID patientId, UUID practitionerId);

  /** Sans contrôle d'accès : l'appelant passe d'abord par {@link PatientOwnershipGuard}. */
  Optional<String> displayName(UUID patientId);

  /** Sans contrôle d'accès : l'appelant passe d'abord par {@link PatientOwnershipGuard}. */
  Optional<LocalDate> birthDate(UUID patientId);
}
