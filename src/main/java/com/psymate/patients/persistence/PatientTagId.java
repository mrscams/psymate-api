package com.psymate.patients.persistence;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clé composite de {@code patient_tag} : (patient_id, tag_id). Le tag appartient à vocabulary. */
@Embeddable
public class PatientTagId implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID patientId;
  private UUID tagId;

  protected PatientTagId() {}

  public PatientTagId(UUID patientId, UUID tagId) {
    this.patientId = Objects.requireNonNull(patientId);
    this.tagId = Objects.requireNonNull(tagId);
  }

  public UUID getPatientId() {
    return patientId;
  }

  public UUID getTagId() {
    return tagId;
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof PatientTagId other
            && Objects.equals(patientId, other.patientId)
            && Objects.equals(tagId, other.tagId));
  }

  @Override
  public int hashCode() {
    return Objects.hash(patientId, tagId);
  }
}
