package com.psymate.agenda.api;

import com.psymate.shared.Reserved;
import java.time.Instant;
import java.util.UUID;

/**
 * Rendez-vous invité rattaché à une fiche patient (G Q60) : {@code patientId} est le patient
 * désormais visé ; l'identité de l'invité a été effacée du rendez-vous.
 */
@Reserved(
    consumer = "M12, synchronisation Google (outbox), ou le premier abonné s'il vient avant",
    reason = "G Q59, Q60 : publié sans abonné, comme point d'ancrage")
public class AppointmentPatientAttached extends AppointmentEvent {

  /** Pour la désérialisation. */
  protected AppointmentPatientAttached() {}

  public AppointmentPatientAttached(
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
