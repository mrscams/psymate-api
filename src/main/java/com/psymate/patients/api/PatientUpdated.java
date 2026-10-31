package com.psymate.patients.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/** Patient updated (événement de domaine, persisté dans le registre d'événements). */
public class PatientUpdated extends DomainEvent {

  private UUID patientId;
  private UUID ownerId;

  /** Pour la désérialisation. */
  protected PatientUpdated() {}

  public PatientUpdated(UUID tenantId, UUID patientId, UUID ownerId) {
    super(tenantId);
    this.patientId = patientId;
    this.ownerId = ownerId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public UUID getOwnerId() {
    return ownerId;
  }
}
