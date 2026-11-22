package com.psymate.agenda.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 *
 * <p>Un rendez-vous vise soit un patient ({@code patient_id}), soit un invité (G Q60, V13) : une
 * personne sans fiche patient, décrite par {@code guest_first_name}, {@code guest_last_name} et
 * {@code guest_phone}, qui ne vit que dans ces colonnes. La purge des invités (6 mois) les met à
 * NULL et pose {@code guest_purged_at} ; le rattachement à une fiche patient pose {@code
 * patient_id} et efface les colonnes invité ({@code ck_appointment_subject}).
 *
 * <p>V15 (M14-04, G Q74, Q82) : l'invité peut aussi avoir un e-mail ({@code guest_email}, purgé et
 * effacé comme les autres colonnes invité), et chaque rendez-vous porte son origine ({@code
 * origin}) : {@code practitioner} pour tout rendez-vous créé par le praticien, {@code online} pour
 * une demande en ligne. L'origine ne change jamais après la création.
 */
@Entity
@Table(name = "appointment")
public class Appointment implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, updatable = false)
  private UUID practitionerId;

  /** Patient visé ; {@code null} pour un rendez-vous invité (G Q60). */
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

  // Invité (G Q60) : null pour un rendez-vous patient, effacés par la purge ou le rattachement.
  private String guestFirstName;
  private String guestLastName;
  private String guestPhone;
  private String guestEmail;
  private OffsetDateTime guestPurgedAt;

  /** Origine (V15, G Q74) : {@code practitioner} sauf demande en ligne. */
  @Convert(converter = AppointmentOriginConverter.class)
  @Column(nullable = false, updatable = false)
  private AppointmentOrigin origin = AppointmentOrigin.PRACTITIONER;

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
    this.patientId = patientId;
    this.seriesId = seriesId;
    this.startsAt = Objects.requireNonNull(startsAt);
    this.endsAt = Objects.requireNonNull(endsAt);
    this.modality = Objects.requireNonNull(modality);
    this.status = tentative ? AppointmentStatus.tentative : AppointmentStatus.planned;
    this.planningNote = planningNote;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = createdAt;
  }

  /**
   * Nouveau rendez-vous invité (G Q60) : aucun patient, nom obligatoire, prénom, téléphone et
   * e-mail facultatifs. Mêmes règles de statut et de créneau qu'un rendez-vous patient.
   */
  public static Appointment forGuest(
      UUID id,
      UUID tenantId,
      UUID practitionerId,
      Guest guest,
      UUID seriesId,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      AppointmentModality modality,
      boolean tentative,
      String planningNote,
      OffsetDateTime createdAt) {
    Appointment a =
        new Appointment(
            id,
            tenantId,
            practitionerId,
            null,
            seriesId,
            startsAt,
            endsAt,
            modality,
            tentative,
            planningNote,
            createdAt);
    a.setGuest(Objects.requireNonNull(guest));
    return a;
  }

  /**
   * Rendez-vous invité né d'une demande en ligne vérifiée (M14-06, G Q74) : origine {@code online},
   * hors série, sans note de planification ; {@code tentative} en mode {@code manual}, {@code
   * planned} en mode {@code auto}.
   */
  public static Appointment online(
      UUID id,
      UUID tenantId,
      UUID practitionerId,
      Guest guest,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      AppointmentModality modality,
      boolean tentative,
      OffsetDateTime createdAt) {
    Appointment a =
        forGuest(
            id,
            tenantId,
            practitionerId,
            guest,
            null,
            startsAt,
            endsAt,
            modality,
            tentative,
            null,
            createdAt);
    a.origin = AppointmentOrigin.ONLINE;
    return a;
  }

  /** Identité d'un invité : nom obligatoire, prénom, téléphone et e-mail (V15) facultatifs. */
  public record Guest(String firstName, String lastName, String phone, String email) {
    public Guest {
      Objects.requireNonNull(lastName);
    }
  }

  private void setGuest(Guest guest) {
    this.guestFirstName = guest.firstName();
    this.guestLastName = guest.lastName();
    this.guestPhone = guest.phone();
    this.guestEmail = guest.email();
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

  /**
   * Corrige l'identité de l'invité (PATCH, G Q60) ; refusé si le rendez-vous est annulé ou terminé
   * (409). L'appelant a vérifié que c'est un invité non purgé.
   */
  public void correctGuest(Guest guest, OffsetDateTime at) {
    requireActive();
    if (!isGuest() || isGuestPurged()) {
      throw new IllegalStateException("not an editable guest appointment");
    }
    setGuest(guest);
    touch(at);
  }

  /**
   * Rattache le rendez-vous invité à la fiche patient créée pour lui (G Q60) : {@code patient_id}
   * posé, colonnes invité effacées, e-mail compris (G Q82 e). 409 APPOINTMENT_NOT_ACTIVE s'il vise
   * déjà un patient ou si l'invité est purgé. Le statut n'entre pas en compte : un rendez-vous
   * annulé se rattache aussi, pour que l'historique du patient soit complet.
   */
  public void attachPatient(UUID patientId, OffsetDateTime at) {
    if (!isGuest() || isGuestPurged()) {
      throw AppointmentException.notActive();
    }
    this.patientId = Objects.requireNonNull(patientId);
    this.guestFirstName = null;
    this.guestLastName = null;
    this.guestPhone = null;
    this.guestEmail = null;
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

  /** Rendez-vous invité (aucun patient), purgé ou non. */
  public boolean isGuest() {
    return patientId == null;
  }

  /** Invité purgé : nom, prénom, téléphone et e-mail effacés (G Q60, Q82). */
  public boolean isGuestPurged() {
    return guestPurgedAt != null;
  }

  public String getGuestFirstName() {
    return guestFirstName;
  }

  public String getGuestLastName() {
    return guestLastName;
  }

  public String getGuestPhone() {
    return guestPhone;
  }

  /** E-mail de l'invité (V15) ; null si inconnu, purgé ou rattaché. */
  public String getGuestEmail() {
    return guestEmail;
  }

  /** Origine du rendez-vous (V15, G Q74). */
  public AppointmentOrigin getOrigin() {
    return origin;
  }

  public OffsetDateTime getGuestPurgedAt() {
    return guestPurgedAt;
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
