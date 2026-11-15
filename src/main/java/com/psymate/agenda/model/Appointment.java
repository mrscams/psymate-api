package com.psymate.agenda.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Rendez-vous du praticien (table {@code appointment}, V12, I2). {@code tenant_id}, {@code
 * practitioner_id}, {@code patient_id} et {@code session_entry_id} sont des UUID nus : aucune
 * association JPA vers un autre module (garde ArchUnit). Jamais supprimé : l'annulation pose {@code
 * cancelled} et {@code cancelled_at} ensemble, comme l'exige {@code ck_appointment_cancel} (G Q53).
 * {@code updated_at} est maintenu par l'entité à chaque modification (E2), {@code created_at} par
 * l'application à la création.
 */
@Entity
@Table(name = "appointment")
public class Appointment implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  @Column(nullable = false, updatable = false)
  private UUID patientId;

  @Column(updatable = false)
  private UUID seriesId;

  @Column(nullable = false)
  private OffsetDateTime startsAt;

  @Column(nullable = false)
  private OffsetDateTime endsAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AppointmentModality modality;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AppointmentStatus status;

  private String planningNote;
  private String cancelReason;
  private OffsetDateTime cancelledAt;
  private UUID sessionEntryId;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  @Transient private boolean isNew = true;

  protected Appointment() {}

  /**
   * Nouveau rendez-vous {@code planned}, ou {@code tentative} si « À confirmer » (G Q52). {@code
   * endsAt} doit suivre {@code startsAt} ({@code ck_appointment_order}).
   */
  public Appointment(
      UUID id,
      UUID tenantId,
      UUID practitionerId,
      UUID patientId,
      UUID seriesId,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      AppointmentModality modality,
      boolean tentative,
      String planningNote,
      OffsetDateTime createdAt) {
    this.id = Objects.requireNonNull(id);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.practitionerId = Objects.requireNonNull(practitionerId);
    this.patientId = Objects.requireNonNull(patientId);
    this.seriesId = seriesId;
    this.startsAt = Objects.requireNonNull(startsAt);
    this.endsAt = Objects.requireNonNull(endsAt);
    this.modality = Objects.requireNonNull(modality);
    this.status = tentative ? AppointmentStatus.tentative : AppointmentStatus.planned;
    this.planningNote = planningNote;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = createdAt;
  }

  // ---- transitions (I3 ; les règles 409 sont celles de l'API)

  private void requireActive() {
    if (!status.isActive()) {
      throw AppointmentException.notActive();
    }
  }

  private void touch(OffsetDateTime at) {
    this.updatedAt = Objects.requireNonNull(at);
  }

  /** Déplace ou redimensionne le créneau ; refusé si annulé ou terminé. */
  public void reschedule(OffsetDateTime startsAt, OffsetDateTime endsAt, OffsetDateTime at) {
    requireActive();
    this.startsAt = Objects.requireNonNull(startsAt);
    this.endsAt = Objects.requireNonNull(endsAt);
    touch(at);
  }

  public void changeModality(AppointmentModality modality, OffsetDateTime at) {
    requireActive();
    this.modality = Objects.requireNonNull(modality);
    touch(at);
  }

  /** {@code null} efface la note (PATCH, I3). */
  public void changePlanningNote(String planningNote, OffsetDateTime at) {
    requireActive();
    this.planningNote = planningNote;
    touch(at);
  }

  /** {@code tentative → planned} ; 409 pour tout autre statut (I3). */
  public void confirm(OffsetDateTime at) {
    if (status != AppointmentStatus.tentative) {
      throw AppointmentException.notActive();
    }
    this.status = AppointmentStatus.planned;
    touch(at);
  }

  /**
   * Annule : idempotent sur un rendez-vous déjà annulé (la raison et la date d'origine sont
   * gardées), refusé si {@code done} (I3). Pose {@code cancelled} et {@code cancelled_at} ensemble
   * (G Q53).
   */
  public void cancel(String reason, OffsetDateTime at) {
    if (status == AppointmentStatus.cancelled) {
      return;
    }
    if (status == AppointmentStatus.done) {
      throw AppointmentException.notActive();
    }
    this.status = AppointmentStatus.cancelled;
    this.cancelReason = reason;
    this.cancelledAt = Objects.requireNonNull(at);
    touch(at);
  }

  /** Rattache la séance créée (M11-08) : {@code done}, seule voie vers ce statut. */
  public void attachSession(UUID sessionEntryId, OffsetDateTime at) {
    requireActive();
    this.sessionEntryId = Objects.requireNonNull(sessionEntryId);
    this.status = AppointmentStatus.done;
    touch(at);
  }

  // ---- lecture

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public UUID getSeriesId() {
    return seriesId;
  }

  public OffsetDateTime getStartsAt() {
    return startsAt;
  }

  public OffsetDateTime getEndsAt() {
    return endsAt;
  }

  public int getDurationMin() {
    return (int) Duration.between(startsAt, endsAt).toMinutes();
  }

  public AppointmentModality getModality() {
    return modality;
  }

  public AppointmentStatus getStatus() {
    return status;
  }

  public String getPlanningNote() {
    return planningNote;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  public OffsetDateTime getCancelledAt() {
    return cancelledAt;
  }

  public UUID getSessionEntryId() {
    return sessionEntryId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
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
    return this == o || (o instanceof Appointment other && id != null && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
