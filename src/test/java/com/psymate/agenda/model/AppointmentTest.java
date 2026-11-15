package com.psymate.agenda.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Transitions de l'entité (I3, G Q52, Q53) : qui est actif, qui refuse, ce que porte l'erreur. */
class AppointmentTest {

  private static final OffsetDateTime T0 =
      OffsetDateTime.of(2026, 11, 16, 9, 0, 0, 0, ZoneOffset.UTC);

  private static Appointment appointment(boolean tentative) {
    return new Appointment(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        T0,
        T0.plusMinutes(50),
        AppointmentModality.video,
        tentative,
        null,
        T0.minusDays(1));
  }

  @Test
  void aNewAppointmentIsPlannedOrTentativeAndCarriesItsDuration() {
    assertThat(appointment(false).getStatus()).isEqualTo(AppointmentStatus.planned);
    assertThat(appointment(true).getStatus()).isEqualTo(AppointmentStatus.tentative);
    assertThat(appointment(false).getDurationMin()).isEqualTo(50);
    assertThat(appointment(false).getUpdatedAt()).isEqualTo(T0.minusDays(1));
  }

  @Test
  void onlyPlannedAndTentativeAreActive() {
    assertThat(AppointmentStatus.planned.isActive()).isTrue();
    assertThat(AppointmentStatus.tentative.isActive()).isTrue();
    assertThat(AppointmentStatus.cancelled.isActive()).isFalse();
    assertThat(AppointmentStatus.done.isActive()).isFalse();
  }

  @Test
  void cancelIsIdempotentAndRefusedOnceDone() {
    Appointment a = appointment(false);
    a.cancel("absent", T0);
    a.cancel("autre", T0.plusHours(1));
    assertThat(a.getStatus()).isEqualTo(AppointmentStatus.cancelled);
    assertThat(a.getCancelReason()).isEqualTo("absent");
    assertThat(a.getCancelledAt()).isEqualTo(T0);
    assertThat(a.getUpdatedAt()).isEqualTo(T0);

    Appointment done = appointment(false);
    done.attachSession(UUID.randomUUID(), T0);
    assertThat(done.getStatus()).isEqualTo(AppointmentStatus.done);
    assertThatThrownBy(() -> done.cancel(null, T0))
        .isInstanceOfSatisfying(
            AppointmentException.class,
            e -> {
              assertThat(e.code()).isEqualTo("APPOINTMENT_NOT_ACTIVE");
              assertThat(e.status().value()).isEqualTo(409);
              assertThat(e.field()).isNull();
            });
  }

  @Test
  void confirmOnlyAcceptsTentative() {
    Appointment a = appointment(true);
    a.confirm(T0);
    assertThat(a.getStatus()).isEqualTo(AppointmentStatus.planned);
    assertThatThrownBy(() -> a.confirm(T0)).isInstanceOf(AppointmentException.class);
  }

  @Test
  void validationErrorsCarryTheirFieldAndStatus() {
    AppointmentException archived = AppointmentException.patientArchived();
    assertThat(archived.status().value()).isEqualTo(422);
    assertThat(archived.field()).isEqualTo("patientId");
    assertThat(archived.errors()).hasSize(1);
    assertThat(archived.errors().get(0).code()).isEqualTo("PATIENT_ARCHIVED");
    assertThat(AppointmentException.tooManyOccurrences().field()).isEqualTo("recurrence");
    assertThat(AppointmentException.windowTooLarge().field()).isEqualTo("to");
    assertThat(AppointmentException.notFound().status().value()).isEqualTo(404);
    assertThat(AppointmentException.seriesNotFound().status().value()).isEqualTo(404);
    assertThat(AppointmentException.notFound().title()).isEqualTo("Not Found");
  }
}
