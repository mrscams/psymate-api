package com.psymate.agenda.api;

import java.time.Instant;
import java.util.UUID;

/** Rendez-vous créé, planned ou tentative (G Q59). */
public class AppointmentPlanned extends AppointmentEvent {

  /** Pour la désérialisation. */
  protected AppointmentPlanned() {}

  public AppointmentPlanned(
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
