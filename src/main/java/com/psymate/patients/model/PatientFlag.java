package com.psymate.patients.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Drapeau posé à la main sur un patient (table {@code patient_flag}). Un seul drapeau actif par
 * patient et par nature ({@code ux_patient_flag_active}).
 */
@Entity
@Table(name = "patient_flag")
public class PatientFlag implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID patientId;

  @Column(nullable = false, updatable = false)
  private String kind;

  private String reason;

  @Column(nullable = false, updatable = false)
  private UUID setBy;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime setAt;

  private UUID clearedBy;
  private OffsetDateTime clearedAt;

  @Transient private boolean isNew = true;

  protected PatientFlag() {}

  public PatientFlag(
      UUID id, UUID patientId, String kind, String reason, UUID setBy, OffsetDateTime now) {
    this.id = Objects.requireNonNull(id);
    this.patientId = Objects.requireNonNull(patientId);
    this.kind = Objects.requireNonNull(kind);
    this.reason = reason;
    this.setBy = Objects.requireNonNull(setBy);
    this.setAt = now;
  }

  /** Lève le drapeau. */
  public void clear(UUID by, OffsetDateTime now) {
    this.clearedBy = Objects.requireNonNull(by);
    this.clearedAt = now;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public String getKind() {
    return kind;
  }

  public String getReason() {
    return reason;
  }

  public UUID getSetBy() {
    return setBy;
  }

  public OffsetDateTime getSetAt() {
    return setAt;
  }

  public UUID getClearedBy() {
    return clearedBy;
  }

  public OffsetDateTime getClearedAt() {
    return clearedAt;
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
    return this == o || (o instanceof PatientFlag other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
