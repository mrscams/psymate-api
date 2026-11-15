package com.psymate.agenda.api;

import com.psymate.shared.Reserved;
import java.time.Instant;
import java.util.UUID;

/** Rendez-vous déplacé ou redimensionné : début ou durée changé (G Q59). */
@Reserved(
    consumer = "M12, synchronisation Google (outbox), ou le premier abonné s'il vient avant",
    reason = "G Q59 : publié dès M11 sans abonné, comme point d'ancrage")
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
