package com.psymate.patients.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.data.domain.Persistable;

/** Tag posé sur un patient (table {@code patient_tag}). Le tag lui-même vit dans vocabulary. */
@Entity
@Table(name = "patient_tag")
public class PatientTag implements Persistable<PatientTagId> {

  @EmbeddedId private PatientTagId id;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime addedAt;

  @Transient private boolean isNew = true;

  protected PatientTag() {}

  public PatientTag(PatientTagId id, OffsetDateTime now) {
    this.id = Objects.requireNonNull(id);
    this.addedAt = now;
  }

  @Override
  public PatientTagId getId() {
    return id;
  }

  public OffsetDateTime getAddedAt() {
    return addedAt;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNew = false;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof PatientTag other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
