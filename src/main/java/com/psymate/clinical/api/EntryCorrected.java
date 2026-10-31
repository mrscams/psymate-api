package com.psymate.clinical.api;

import com.psymate.events.api.DomainEvent;
import java.util.UUID;

/**
 * Entry corrected : porte les deux versions (précédente et nouvelle). Jamais émis pour une
 * modification en période de grâce (A4.8).
 */
public class EntryCorrected extends DomainEvent {

  private UUID entryId;
  private UUID patientId;
  private String entryType;
  private int previousVersion;
  private int newVersion;

  /** Pour la désérialisation. */
  protected EntryCorrected() {}

  public EntryCorrected(
      UUID tenantId,
      UUID entryId,
      UUID patientId,
      String entryType,
      int previousVersion,
      int newVersion) {
    super(tenantId);
    this.entryId = entryId;
    this.patientId = patientId;
    this.entryType = entryType;
    this.previousVersion = previousVersion;
    this.newVersion = newVersion;
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

  public int getPreviousVersion() {
    return previousVersion;
  }

  public int getNewVersion() {
    return newVersion;
  }
}
