package com.psymate.patients.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/** Patient archived (événement de domaine, persisté dans le registre d'événements). */
public class PatientArchived extends DomainEvent {

  private UUID patientId;
  private UUID ownerId;

  /** Pour la désérialisation. */
  protected PatientArchived() {}

  public PatientArchived(UUID tenantId, UUID patientId, UUID ownerId) {
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
