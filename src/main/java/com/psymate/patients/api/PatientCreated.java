package com.psymate.patients.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/** Patient created (événement de domaine, persisté dans le registre d'événements). */
public class PatientCreated extends DomainEvent {

  private UUID patientId;
  private UUID ownerId;

  /** Pour la désérialisation. */
  protected PatientCreated() {}

  public PatientCreated(UUID tenantId, UUID patientId, UUID ownerId) {
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
