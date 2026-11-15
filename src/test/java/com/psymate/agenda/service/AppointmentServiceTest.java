package com.psymate.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.agenda.api.AppointmentCancelled;
import com.psymate.agenda.api.AppointmentPlanned;
import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentException;
import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.AppointmentStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Règles pures du cycle de vie (M11-05) : calcul de la fin, chevauchement aux bornes (intervalles
 * semi-ouverts, même règle que la requête JPQL), transitions de statut, payload des événements.
 */
class AppointmentServiceTest {

  private static final OffsetDateTime NINE = OffsetDateTime.parse("2026-11-16T09:00:00Z");
  private static final OffsetDateTime TEN = OffsetDateTime.parse("2026-11-16T10:00:00Z");

  private static Appointment appointment(boolean tentative) {
    return new Appointment(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        NINE,
        AppointmentService.endsAt(NINE, 50),
        AppointmentModality.in_person,
        tentative,
        null,
        NINE.minusDays(1));
  }

  @Test
  void endsAtIsStartPlusDuration() {
    assertThat(AppointmentService.endsAt(NINE, 50))
        .isEqualTo(OffsetDateTime.parse("2026-11-16T09:50:00Z"));
    assertThat(AppointmentService.endsAt(NINE, 600))
        .isEqualTo(OffsetDateTime.parse("2026-11-16T19:00:00Z"));
  }

  @Test
  void anAppointmentEndingAtTenDoesNotOverlapOneStartingAtTen() {
    assertThat(AppointmentService.overlaps(NINE, TEN, TEN, TEN.plusMinutes(50))).isFalse();
    assertThat(AppointmentService.overlaps(TEN, TEN.plusMinutes(50), NINE, TEN)).isFalse();
  }

  @Test
  void oneMinuteOfCommonTimeIsAnOverlap() {
    assertThat(AppointmentService.overlaps(NINE, TEN.plusMinutes(1), TEN, TEN.plusMinutes(50)))
        .isTrue();
    // Inclusion et identité.
    assertThat(AppointmentService.overlaps(NINE, TEN, NINE.plusMinutes(10), NINE.plusMinutes(20)))
        .isTrue();
    assertThat(AppointmentService.overlaps(NINE, TEN, NINE, TEN)).isTrue();
  }

  @Test
  void receivedInstantsAreUtcToTheMicrosecond() {
    OffsetDateTime t = AppointmentService.utc(Instant.parse("2026-11-16T09:00:00.123456789Z"));
    assertThat(t.getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(t.getNano()).isEqualTo(123_456_000);
  }

  @Test
  void newAppointmentIsPlannedOrTentative() {
    assertThat(appointment(false).getStatus()).isEqualTo(AppointmentStatus.planned);
    assertThat(appointment(true).getStatus()).isEqualTo(AppointmentStatus.tentative);
    assertThat(appointment(false).getDurationMin()).isEqualTo(50);
  }

  @Test
  void confirmTurnsTentativeIntoPlannedAndRefusesAnythingElse() {
    Appointment tentative = appointment(true);
    tentative.confirm(TEN);
    assertThat(tentative.getStatus()).isEqualTo(AppointmentStatus.planned);
    assertThatThrownBy(() -> tentative.confirm(TEN))
        .isInstanceOf(AppointmentException.class)
        .hasFieldOrPropertyWithValue("code", "APPOINTMENT_NOT_ACTIVE");
  }

  @Test
  void cancelIsIdempotentAndCancelledCannotBeRescheduled() {
    Appointment a = appointment(false);
    a.cancel("Indisponible", TEN);
    a.cancel("Autre motif", TEN.plusHours(1));
    assertThat(a.getStatus()).isEqualTo(AppointmentStatus.cancelled);
    assertThat(a.getCancelReason()).isEqualTo("Indisponible");
    assertThat(a.getCancelledAt()).isEqualTo(TEN);
    assertThatThrownBy(() -> a.reschedule(TEN, TEN.plusMinutes(50), TEN))
        .isInstanceOf(AppointmentException.class)
        .hasFieldOrPropertyWithValue("code", "APPOINTMENT_NOT_ACTIVE");
    assertThatThrownBy(() -> a.confirm(TEN)).isInstanceOf(AppointmentException.class);
  }

  @Test
  void doneCannotBeCancelled() {
    Appointment a = appointment(false);
    a.attachSession(UUID.randomUUID(), TEN);
    assertThatThrownBy(() -> a.cancel(null, TEN))
        .isInstanceOf(AppointmentException.class)
        .hasFieldOrPropertyWithValue("code", "APPOINTMENT_NOT_ACTIVE");
  }

  @Test
  void eventsCarryTheQ59Payload() {
    Appointment a = appointment(true);
    AppointmentPlanned planned = AppointmentService.event(AppointmentPlanned::new, a);
    assertThat(planned.getAppointmentId()).isEqualTo(a.getId());
    assertThat(planned.getTenantId()).isEqualTo(a.getTenantId());
    assertThat(planned.getPractitionerId()).isEqualTo(a.getPractitionerId());
    assertThat(planned.getPatientId()).isEqualTo(a.getPatientId());
    assertThat(planned.getSeriesId()).isNull();
    assertThat(planned.getStartsAt()).isEqualTo(NINE.toInstant());
    assertThat(planned.getEndsAt()).isEqualTo(NINE.plusMinutes(50).toInstant());
    assertThat(planned.getStatus()).isEqualTo("tentative");

    a.cancel(null, TEN);
    assertThat(AppointmentService.event(AppointmentCancelled::new, a).getStatus())
        .isEqualTo("cancelled");
  }
}
