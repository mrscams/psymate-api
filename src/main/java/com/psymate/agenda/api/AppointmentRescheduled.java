package com.psymate.agenda.api;

import java.time.Instant;
import java.util.UUID;

/** Rendez-vous déplacé ou redimensionné : début ou durée changé (G Q59). */
public class AppointmentRescheduled extends AppointmentEvent {

  /** Pour la désérialisation. */
  protected AppointmentRescheduled() {}

  public AppointmentRescheduled(
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
