package com.psymate.patients.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lecture minimale d'un patient pour les autres modules. */
public interface PatientFacade {

  /** Le patient existe (archivé ou non). */
  boolean exists(UUID patientId);

  /** Le patient appartient à ce praticien ET, s'il y a une session, au cabinet de celle-ci. */
  boolean ownedBy(UUID patientId, UUID practitionerId);

  /**
   * Tous les patients de ce praticien, archivés compris (G Q18 : l'export du compte est la seule
   * sortie des données d'un compte refusé ; l'archivage n'est qu'une désactivation douce), triés
   * par nom affiché. Sans session requise : l'appelant a déjà authentifié le praticien (compte
   * suspendu par identifiants).
   */
  List<PatientRef> ownedBy(UUID practitionerId);

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
