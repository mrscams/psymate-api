package com.psymate.agenda.api;

import java.time.Instant;
import java.util.UUID;

/** Rendez-vous annulé ; jamais publié pour une annulation répétée (G Q59). */
public class AppointmentCancelled extends AppointmentEvent {

  /** Pour la désérialisation. */
  protected AppointmentCancelled() {}

  public AppointmentCancelled(
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
