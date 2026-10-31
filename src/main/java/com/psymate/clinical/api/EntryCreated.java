package com.psymate.clinical.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/** Entry created (événement de domaine, persisté dans le registre d'événements). */
public class EntryCreated extends DomainEvent {

  private UUID entryId;
  private UUID patientId;
  private String entryType;
  private int version;

  /** Pour la désérialisation. */
  protected EntryCreated() {}

  public EntryCreated(UUID tenantId, UUID entryId, UUID patientId, String entryType, int version) {
    super(tenantId);
    this.entryId = entryId;
    this.patientId = patientId;
    this.entryType = entryType;
    this.version = version;
  }

  public UUID getEntryId() {
    return entryId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public String getEntryType() {
    return entryType;
  }

  /** Version courante de l'entrée au moment de l'événement. */
  public int getVersion() {
    return version;
  }
}
