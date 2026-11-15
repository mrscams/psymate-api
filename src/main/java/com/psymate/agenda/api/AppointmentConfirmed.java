package com.psymate.agenda.api;

import java.time.Instant;
import java.util.UUID;

/** Rendez-vous confirmé : tentative devenu planned (G Q59). */
public class AppointmentConfirmed extends AppointmentEvent {

  /** Pour la désérialisation. */
  protected AppointmentConfirmed() {}

  public AppointmentConfirmed(
      UUID tenantId,
      UUID appointmentId,
      UUID practitionerId,
      UUID patientId,
      UUID seriesId,
      Instant startsAt,
      Instant endsAt,
      String status) {
    super(tenantId, appointmentId, practitionerId, patientId, seriesId, startsAt, endsAt, status);
  }
}
