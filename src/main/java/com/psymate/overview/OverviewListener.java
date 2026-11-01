package com.psymate.overview;

import com.psymate.clinical.api.EntryArchived;
import com.psymate.clinical.api.EntryCorrected;
import com.psymate.clinical.api.EntryCreated;
import com.psymate.linking.api.LinkAccepted;
import com.psymate.patients.api.PatientUpdated;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Rafraîchit {@code patient_overview} après chaque événement qui change le dossier d'un patient
 * (A6, « Vue d'ensemble »). Chaque abonné recalcule la projection entière depuis l'état courant de
 * la base, avec {@code source_rev = patient.clinical_rev} lu dans la transaction du calcul.
 *
 * <p>Idempotence (events.api.EventDeduplicator) : l'effet est un upsert déterministe qui ne dépend
 * que de l'état de la base, jamais du contenu de l'événement, et qui ne remplace jamais une ligne
 * de révision plus récente ; une double livraison ou un rejeu produit donc la même ligne, sans
 * déduplication. Un événement sur un patient inconnu est ignoré sans exception.
 */
@Component
class OverviewListener {

  private final OverviewService service;

  OverviewListener(OverviewService service) {
    this.service = service;
  }

  @ApplicationModuleListener(id = "overview.entry-created")
  void on(EntryCreated event) {
    refresh(event.getPatientId());
  }

  @ApplicationModuleListener(id = "overview.entry-corrected")
  void on(EntryCorrected event) {
    refresh(event.getPatientId());
  }

  @ApplicationModuleListener(id = "overview.entry-archived")
  void on(EntryArchived event) {
    refresh(event.getPatientId());
  }

  @ApplicationModuleListener(id = "overview.link-accepted")
  void on(LinkAccepted event) {
    refresh(event.getPatientId());
  }

  @ApplicationModuleListener(id = "overview.patient-updated")
  void on(PatientUpdated event) {
    refresh(event.getPatientId());
  }

  private void refresh(UUID patientId) {
    service.recompute(patientId);
  }
}
