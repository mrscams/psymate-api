package com.psymate.identity.model;

import com.psymate.shared.time.Timestamps;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * Recours d'un compte refusé ou suspendu (table {@code practitioner_appeal}, G Q28) : un seul par
 * décision (index unique {@code ux_appeal_decision}). Seul l'examen par un administrateur modifie
 * la ligne, d'où {@code @DynamicUpdate} et les colonnes du dépôt non modifiables.
 */
@Entity
@DynamicUpdate
@Table(name = "practitioner_appeal")
public class PractitionerAppeal implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private UUID decisionId;

  @Column(nullable = false, updatable = false)
  private String message;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime submittedAt;

  private UUID reviewedBy;
  private OffsetDateTime reviewedAt;
  private String outcome;
  private String responseMessage;

  @Transient private boolean isNew = true;

  protected PractitionerAppeal() {}

  /** Recours déposé par le praticien lui-même, pas encore examiné. */
  public PractitionerAppeal(
      UUID id, UUID practitionerId, UUID decisionId, String message, Instant submittedAt) {
    this.id = Objects.requireNonNull(id);
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.decisionId = Objects.requireNonNull(decisionId);
    this.message = Objects.requireNonNull(message);
    this.submittedAt = Timestamps.utc(submittedAt);
  }

  /**
   * Examen par un administrateur.
   *
   * @param outcome {@code accepted} ou {@code rejected} (valeurs de la contrainte CHECK)
   */
  public void decide(UUID adminId, String outcome, String responseMessage, Instant now) {
    this.reviewedBy = Objects.requireNonNull(adminId);
    this.reviewedAt = Timestamps.utc(now);
    this.outcome = Objects.requireNonNull(outcome);
    this.responseMessage = responseMessage;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public UUID getDecisionId() {
    return decisionId;
  }

  public String getMessage() {
    return message;
  }

  public OffsetDateTime getSubmittedAt() {
    return submittedAt;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public OffsetDateTime getReviewedAt() {
    return reviewedAt;
  }

  public String getOutcome() {
    return outcome;
  }

  public String getResponseMessage() {
    return responseMessage;
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
        || (o instanceof PractitionerAppeal other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
