package com.psymate.patients.api;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
   * Cadre du suivi (depuis quand, rythme, modalité, durée de séance) lu dans {@code
   * patient.follow_up} : vide si le patient n'existe pas, sinon présent même quand le JSON est vide
   * (quatre champs null). Lecture seule, sans contrôle d'accès : l'appelant passe d'abord par
   * {@link PatientOwnershipGuard}. Ni {@code fee} ni {@code prescriber} (G Q56, M11-03).
   */
  Optional<FollowUpView> followUp(UUID patientId);

  /**
   * Identité de facturation d'un patient, archivé ou non (M15-04, G Q85) : vide si le patient
   * n'existe pas. Sans contrôle d'accès : l'appelant passe d'abord par {@link
   * PatientOwnershipGuard} ou compare {@link PatientBillingView#ownerId()} au praticien courant. Ni
   * {@code fee}, ni {@code prescriber}, ni contexte, ni téléphone (G Q56).
   */
  Optional<PatientBillingView> billingIdentity(UUID patientId);

  /**
   * Identités de facturation de plusieurs patients, archivés compris, en une seule requête (M15-04,
   * G Q85), indexées par identifiant ; les identifiants inconnus et les null sont ignorés, une
   * collection vide donne une map vide sans requête. Même absence de contrôle d'accès que {@link
   * #billingIdentity(UUID)} : l'appelant écarte les vues dont {@code ownerId} n'est pas le sien.
   */
  Map<UUID, PatientBillingView> billingIdentities(Collection<UUID> patientIds);

  /**
   * Incrémente patient.clinical_rev : à appeler par les services d'écriture clinical et linking à
   * chaque écriture clinique (E7 O10, tranché par G Q30 : « application »). Sans contrôle d'accès
   * (l'appelant a déjà passé par {@link PatientOwnershipGuard}, ou réagit à un événement).
   */
  void touchClinicalRev(UUID patientId);
}
