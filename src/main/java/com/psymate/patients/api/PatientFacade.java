package com.psymate.patients.api;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Lecture minimale d'un patient pour les autres modules. */
public interface PatientFacade {

  /** Le patient existe (archivé ou non). */
  boolean exists(UUID patientId);

  /** Le patient appartient à ce praticien (et à son cabinet). */
  boolean ownedBy(UUID patientId, UUID practitionerId);

  Optional<String> displayName(UUID patientId);

  Optional<LocalDate> birthDate(UUID patientId);
}
