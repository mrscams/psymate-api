package com.psymate.patients.api;

import com.psymate.events.api.DomainEvent;
import com.psymate.shared.Reserved;
import java.util.UUID;

/**
 * Patient archived (événement de domaine, persisté dans le registre d'événements). Publié dès la V1
 * sans abonné, comme point d'ancrage (G Q40).
 */
@Reserved(
    consumer = "V2, ou le premier abonné s'il vient avant",
    reason = "Publié dès la V1 comme point d'ancrage (A4.9)")
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
