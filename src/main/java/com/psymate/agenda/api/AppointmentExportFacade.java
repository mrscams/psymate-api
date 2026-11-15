package com.psymate.agenda.api;

import java.util.List;
import java.util.UUID;

/**
 * Lecture des rendez-vous pour l'export du compte et du dossier (G Q61, M11-11), en lecture seule,
 * tous statuts confondus, triés par début puis par identifiant.
 *
 * <p>Aucun contrôle d'accès ici : le seul appelant, {@code clinical.export}, agit déjà pour le
 * praticien propriétaire. {@link #allOfPractitioner} reçoit l'identifiant du praticien du travail
 * d'export ; {@link #ofPatient} est appelé après la garde de propriété du patient ({@code
 * PatientOwnershipGuard}) ou pour un patient de la liste du praticien.
 */
public interface AppointmentExportFacade {

  /** Tous les rendez-vous du praticien : patients et invités, purgés compris. */
  List<AppointmentExportView> allOfPractitioner(UUID practitionerId);

  /** Les rendez-vous d'un patient (jamais d'invité). */
  List<AppointmentExportView> ofPatient(UUID patientId);
}
