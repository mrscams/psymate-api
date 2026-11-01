package com.psymate.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
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
 * Décision d'administration (table {@code verification_decision}). Append-only : REVOKE UPDATE,
 * DELETE pour psymate_app, donc {@code @Immutable} (INSERT seulement). La note interne n'est jamais
 * transmise au praticien.
 */
@Entity
@Immutable
@Table(name = "verification_decision")
public class VerificationDecision implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false)
  private UUID practitionerId;

  @Column(nullable = false)
  private UUID adminId;

  @Column(nullable = false)
  private String kind;

  private String internalNote;

  private String messageToPractitioner;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private Map<String, Object> checklist;

  private UUID relatedId;

  @Column(nullable = false)
  private OffsetDateTime decidedAt;

  @Transient private boolean isNew = true;

  protected VerificationDecision() {}

  public VerificationDecision(
      UUID id,
      UUID practitionerId,
      UUID adminId,
      String kind,
      String internalNote,
      String messageToPractitioner,
      Map<String, Object> checklist,
      UUID relatedId,
      Instant decidedAt) {
    this.id = Objects.requireNonNull(id);
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.adminId = Objects.requireNonNull(adminId);
    this.kind = Objects.requireNonNull(kind);
    this.internalNote = internalNote;
    this.messageToPractitioner = messageToPractitioner;
    this.checklist = checklist == null ? new LinkedHashMap<>() : new LinkedHashMap<>(checklist);
    this.relatedId = relatedId;
    this.decidedAt = Times.utc(decidedAt);
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public UUID getAdminId() {
    return adminId;
  }

  public String getKind() {
    return kind;
  }

  public String getInternalNote() {
    return internalNote;
  }

  public String getMessageToPractitioner() {
    return messageToPractitioner;
  }

  public Map<String, Object> getChecklist() {
    return checklist;
  }

  public UUID getRelatedId() {
    return relatedId;
  }

  public OffsetDateTime getDecidedAt() {
    return decidedAt;
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
    return this == o
        || (o instanceof VerificationDecision other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
