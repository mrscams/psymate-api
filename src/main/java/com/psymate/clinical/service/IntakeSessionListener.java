package com.psymate.clinical.service;

import com.psymate.patients.api.PatientCreated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Crée la séance d'accueil d'un patient dès sa création (ADR-10, A7 séquence 1). */
@Component
class IntakeSessionListener {

  private final EntryService entries;

  IntakeSessionListener(EntryService entries) {
    this.entries = entries;
  }

  @ApplicationModuleListener
  void on(PatientCreated event) {
    entries.createIntakeSession(
        event.getTenantId(), event.getPatientId(), event.getOwnerId(), event.getOccurredAt());
  }
}
