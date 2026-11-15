package com.psymate.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.agenda.dto.AppointmentAssembler;
import com.psymate.agenda.dto.GuestRequest;
import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentException;
import com.psymate.agenda.model.AppointmentModality;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Règles pures des rendez-vous invités (M11-10, G Q60) : identité normalisée, comparaison « même
 * invité » insensible à la casse et aux espaces, nom affiché, rattachement.
 */
class GuestRulesTest {

  private static final OffsetDateTime NINE = OffsetDateTime.parse("2031-03-10T09:00:00Z");

  private static Appointment guest(String first, String last, String phone) {
    return Appointment.forGuest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new Appointment.Guest(first, last, phone),
        null,
        NINE,
        NINE.plusMinutes(50),
        AppointmentModality.in_person,
        false,
        null,
        NINE.minusDays(1));
  }

  @Test
  void guestOfStripsAndTurnsBlankOptionalFieldsIntoNull() {
    Appointment.Guest g = AppointmentService.guestOf(new GuestRequest("  ", " Benali ", ""));
    assertThat(g).isEqualTo(new Appointment.Guest(null, "Benali", null));
    assertThat(AppointmentService.guestOf(new GuestRequest(" Sara", "Benali", " 06 12 ")))
        .isEqualTo(new Appointment.Guest("Sara", "Benali", "06 12"));
  }

  @Test
  void mergedKeepsAbsentFirstNameAndPhoneAndErasesEmptyOnes() {
    Appointment.Guest current = new Appointment.Guest("Sara", "Benali", "06");
    assertThat(AppointmentService.merged(current, new GuestRequest(null, " Ben Ali ", null)))
        .isEqualTo(new Appointment.Guest("Sara", "Ben Ali", "06"));
    assertThat(AppointmentService.merged(current, new GuestRequest("", "Benali", " ")))
        .isEqualTo(new Appointment.Guest(null, "Benali", null));
  }

  @Test
  void sameGuestIgnoresCaseAndSpaces() {
    Appointment a = guest("Sara", "Benali", "+33 6 12 34 56 78");
    assertThat(AppointmentService.sameGuest(a, guest("sara", "BENALI", "+33612345678"))).isTrue();
    assertThat(AppointmentService.sameGuest(a, guest("Sa ra", "Ben ali", "+33 612345678")))
        .isTrue();
    assertThat(AppointmentService.sameGuest(a, guest("Yanis", "Benali", "+33 6 12 34 56 78")))
        .isFalse();
    assertThat(AppointmentService.sameGuest(a, guest("Sara", "Benali", null))).isFalse();
    assertThat(AppointmentService.sameGuest(guest(null, "Benali", null), guest("", "benali", null)))
        .isTrue();
  }

  @Test
  void displayNameIsFirstThenLastNameOrPurgedLabel() {
    assertThat(AppointmentAssembler.guestDisplayName("Sara", "Benali", false))
        .isEqualTo("Sara Benali");
    assertThat(AppointmentAssembler.guestDisplayName(null, "Benali", false)).isEqualTo("Benali");
    assertThat(AppointmentAssembler.guestDisplayName(null, null, true)).isEqualTo("Invité purgé");
  }

  @Test
  void attachPatientClearsGuestAndRefusesASecondAttach() {
    Appointment a = guest("Sara", "Benali", "06");
    assertThat(a.isGuest()).isTrue();
    UUID patient = UUID.randomUUID();
    a.attachPatient(patient, NINE);
    assertThat(a.isGuest()).isFalse();
    assertThat(a.getPatientId()).isEqualTo(patient);
    assertThat(a.getGuestFirstName()).isNull();
    assertThat(a.getGuestLastName()).isNull();
    assertThat(a.getGuestPhone()).isNull();
    assertThatThrownBy(() -> a.attachPatient(UUID.randomUUID(), NINE))
        .isInstanceOf(AppointmentException.class)
        .satisfies(e -> assertThat(((AppointmentException) e).status().value()).isEqualTo(409));
  }

  @Test
  void phonePatternAcceptsDigitsSpacesPlusDotsDashesParentheses() {
    assertThat("+33 (0)6 12.34-56 78").matches(GuestRequest.PHONE);
    assertThat("0612345678").matches(GuestRequest.PHONE);
    assertThat("").matches(GuestRequest.PHONE);
    assertThat("abc").doesNotMatch(GuestRequest.PHONE);
    assertThat("+ - .").doesNotMatch(GuestRequest.PHONE);
  }
}
