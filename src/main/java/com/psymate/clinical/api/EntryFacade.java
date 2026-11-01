package com.psymate.clinical.api;

import java.util.Collection;
import java.util.Map;
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

  /**
   * Patient de l'entrée si elle existe (archivée ou non), quel que soit son propriétaire. Sans
   * garde de propriété : l'appelant passe par PatientOwnershipGuard.requireOwned, comme clinical
   * (403 si le patient est d'un autre praticien). Utilisé par linking pour rattacher un lien à son
   * patient.
   */
  Optional<UUID> patientOf(UUID entryId);

  /**
   * Ajuste la projection {@code entry.link_count} de {@code delta} (positif ou négatif), sans
   * descendre sous zéro. Aucune garde de propriété : l'appelant (linking) a déjà passé ses propres
   * gardes, ou réagit à un événement (EntryArchived, sans praticien courant). Rejoint la
   * transaction de l'appelant.
   */
  void adjustLinkCount(UUID entryId, int delta);

  /**
   * L'entrée est archivée (archived_at renseigné). Sans garde de propriété : l'appelant (linking) a
   * déjà passé PatientOwnershipGuard.requireOwned ; faux si l'entrée n'existe pas.
   */
  boolean isArchived(UUID entryId);

  /**
   * Lecture par lot, en une requête, des entrées demandées (graphe de linking). Comme les autres
   * lectures de la façade (ADR-09), seules les entrées des patients du praticien courant sont
   * rendues : les identifiants inconnus ou d'un autre praticien sont absents de la map, et la map
   * est vide sans principal. Les entrées archivées sont incluses avec {@code archived = true}.
   * L'appelant (linking) a déjà passé PatientOwnershipGuard.requireOwned et filtre encore sur
   * {@link EntrySummary#patientId()}.
   */
  Map<UUID, EntrySummary> summariesOf(Collection<UUID> entryIds);
}
