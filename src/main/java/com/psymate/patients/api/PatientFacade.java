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

  /**
   * Incrémente patient.clinical_rev : à appeler par les services d'écriture clinical et linking à
   * chaque écriture clinique (E7 O10, tranché par G Q30 : « application »). Sans contrôle d'accès
   * (l'appelant a déjà passé par {@link PatientOwnershipGuard}, ou réagit à un événement).
   */
  void touchClinicalRev(UUID patientId);
}
