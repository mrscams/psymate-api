package com.psymate.patients.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Instantané de la fiche patient (table {@code patient_version}). Append-only : REVOKE UPDATE,
 * DELETE pour psymate_app, donc {@code @Immutable} (aucun UPDATE ni DELETE n'est jamais émis).
 */
@Entity
@Immutable
@Table(name = "patient_version")
public class PatientVersion implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false)
  private UUID patientId;

  @Column(nullable = false)
  private int version;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, Object> snapshot;

  private String reason;

  @Column(nullable = false)
  private UUID authorId;

  @Column(nullable = false)
  private OffsetDateTime createdAt;

  @Transient private boolean isNew = true;

  protected PatientVersion() {}

  public PatientVersion(
      UUID id,
      UUID patientId,
      int version,
      Map<String, Object> snapshot,
      UUID authorId,
      String reason,
      OffsetDateTime now) {
    this.id = Objects.requireNonNull(id);
    this.patientId = Objects.requireNonNull(patientId);
    this.version = version;
    this.snapshot = new LinkedHashMap<>(snapshot);
    this.authorId = Objects.requireNonNull(authorId);
    this.reason = reason;
    this.createdAt = now;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public int getVersion() {
    return version;
  }

  /** Vue de lecture : copiez-la avant de la sortir du module. */
  public Map<String, Object> getSnapshot() {
    return snapshot;
  }

  public String getReason() {
    return reason;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
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
    return this == o || (o instanceof PatientVersion other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
