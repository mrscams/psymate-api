package com.psymate.agenda.api;

import com.psymate.events.api.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Base des événements de domaine d'un rendez-vous (G Q59), persistés dans le registre d'événements.
 * Payload commun : rendez-vous, praticien, patient, série, créneau (UTC) et statut après la
 * transition. Aucun abonné en V1 : ce sont les hooks de la synchronisation externe (M12).
 */
public abstract class AppointmentEvent extends DomainEvent {

  private UUID appointmentId;
  private UUID practitionerId;
  private UUID patientId;
  private UUID seriesId;
  private Instant startsAt;
  private Instant endsAt;
  private String status;

  /** Pour la désérialisation. */
  protected AppointmentEvent() {}

  protected AppointmentEvent(
      UUID tenantId,
      UUID appointmentId,
      UUID practitionerId,
      UUID patientId,
      UUID seriesId,
      Instant startsAt,
      Instant endsAt,
      String status) {
    super(tenantId);
    this.appointmentId = appointmentId;
    this.practitionerId = practitionerId;
    this.patientId = patientId;
    this.seriesId = seriesId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.status = status;
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public UUID getPractitionerId() {
    return practitionerId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  /** Série de l'occurrence, {@code null} pour un rendez-vous isolé. */
  public UUID getSeriesId() {
    return seriesId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  /** Statut après la transition : planned, tentative, cancelled ou done. */
  public String getStatus() {
    return status;
  }
}
