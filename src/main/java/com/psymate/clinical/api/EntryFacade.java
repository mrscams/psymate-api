package com.psymate.clinical.api;

import java.util.Optional;
import java.util.UUID;

/** Lecture minimale des entrées pour les autres modules (linking, overview, search…). */
public interface EntryFacade {

  /** L'entrée existe (archivée ou non) et son patient appartient au praticien courant. */
  boolean exists(UUID entryId);

  /** L'entrée existe et appartient à ce patient. */
  boolean belongsToPatient(UUID entryId, UUID patientId);

  /** Type de l'entrée (note, event, situation, feeling, session, medication). */
  Optional<String> typeOf(UUID entryId);

  /**
   * Les deux entrées existent, sont du même patient, et ce patient appartient au praticien courant.
   */
  boolean sameOwnedPatient(UUID entryA, UUID entryB);

  /**
   * Version courante de l'entrée, sans contrôle d'accès (l'appelant passe d'abord par le garde).
   */
  Optional<EntryVersionView> currentVersionOf(UUID entryId);

  /**
   * Séance courante du patient (ouverte, sinon dernière clôturée, sinon accueil). Vide sans
   * principal, si le patient n'appartient pas au praticien courant, ou s'il n'a aucune séance.
   */
  Optional<SessionView> currentSession(UUID patientId);

  /**
   * Séance ouverte du patient (l'accueil n'en est jamais une). Vide sans principal, si le patient
   * n'appartient pas au praticien courant, ou si aucune séance n'est ouverte.
   */
  Optional<SessionView> openSessionOf(UUID patientId);
}
