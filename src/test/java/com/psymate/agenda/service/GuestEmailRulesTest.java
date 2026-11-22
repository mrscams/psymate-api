package com.psymate.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.agenda.dto.GuestRequest;
import com.psymate.agenda.model.Appointment;
import com.psymate.agenda.model.AppointmentModality;
import com.psymate.agenda.model.AppointmentOrigin;
import com.psymate.agenda.model.AppointmentOriginConverter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Règle de l'e-mail d'un invité (M14-04, G Q82) : format et longueur de {@code guest.email}, chaîne
 * vide = aucun, absent = inchangé au PATCH, effacé au rattachement ; et l'origine d'un rendez-vous
 * créé par le praticien.
 */
class GuestEmailRulesTest {

  private static final OffsetDateTime NINE = OffsetDateTime.parse("2031-03-10T09:00:00Z");

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void validator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void close() {
    factory.close();
  }

  private static GuestRequest withEmail(String email) {
    return new GuestRequest("Sara", "Benali", null, email);
  }

  /** Violations sur email : message (code porté par la contrainte) ou nom de la contrainte. */
  private static Set<ConstraintViolation<GuestRequest>> emailViolations(String email) {
    return validator.validateProperty(withEmail(email), "email");
  }

  private static Appointment guest(String email) {
    return Appointment.forGuest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        new Appointment.Guest("Sara", "Benali", "06", email),
        null,
        NINE,
        NINE.plusMinutes(50),
        AppointmentModality.in_person,
        false,
        null,
        NINE.minusDays(1));
  }

  @Test
  void emailPatternAcceptsAnAddressOrNothing() {
    assertThat("sara@example.test").matches(GuestRequest.EMAIL);
    assertThat("sara.benali+rdv@mail.example.fr").matches(GuestRequest.EMAIL);
    assertThat("").matches(GuestRequest.EMAIL);
    assertThat("abc").doesNotMatch(GuestRequest.EMAIL);
    assertThat("sara@example").doesNotMatch(GuestRequest.EMAIL);
    assertThat("sara@@example.test").doesNotMatch(GuestRequest.EMAIL);
    assertThat("sara benali@example.test").doesNotMatch(GuestRequest.EMAIL);
    assertThat("@example.test").doesNotMatch(GuestRequest.EMAIL);
  }

  @Test
  void validEmptyOrAbsentEmailHasNoViolation() {
    assertThat(emailViolations("sara@example.test")).isEmpty();
    assertThat(emailViolations("")).isEmpty();
    assertThat(emailViolations(null)).isEmpty();
  }

  @Test
  void malformedEmailCarriesTheInvalidFormatCode() {
    Set<ConstraintViolation<GuestRequest>> violations = emailViolations("abc");
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getMessage()).isEqualTo(GuestRequest.INVALID_FORMAT);
  }

  @Test
  void emailOfMoreThan320CharactersIsOutOfRangeOnly() {
    String local = "a".repeat(64);
    String exactly320 = local + "@" + "d".repeat(320 - 64 - 1 - 5) + ".test";
    assertThat(exactly320).hasSize(320);
    assertThat(emailViolations(exactly320)).isEmpty();

    Set<ConstraintViolation<GuestRequest>> violations = emailViolations("a" + exactly320);
    assertThat(violations).hasSize(1);
    assertThat(
            violations
                .iterator()
                .next()
                .getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName())
        .isEqualTo("Size");
  }

  @Test
  void guestOfStripsTheEmailAndTurnsBlankIntoNull() {
    assertThat(AppointmentService.guestOf(withEmail(" sara@example.test ")).email())
        .isEqualTo("sara@example.test");
    assertThat(AppointmentService.guestOf(withEmail("")).email()).isNull();
    assertThat(AppointmentService.guestOf(withEmail(null)).email()).isNull();
  }

  @Test
  void mergedKeepsAnAbsentEmailErasesAnEmptyOneAndReplacesAnother() {
    Appointment.Guest current = new Appointment.Guest("Sara", "Benali", "06", "sara@example.test");
    assertThat(AppointmentService.merged(current, withEmail(null)).email())
        .isEqualTo("sara@example.test");
    assertThat(AppointmentService.merged(current, withEmail("")).email()).isNull();
    assertThat(AppointmentService.merged(current, withEmail("s.benali@example.test")).email())
        .isEqualTo("s.benali@example.test");
  }

  @Test
  void attachPatientAlsoClearsTheEmail() {
    Appointment a = guest("sara@example.test");
    assertThat(a.getGuestEmail()).isEqualTo("sara@example.test");
    a.attachPatient(UUID.randomUUID(), NINE);
    assertThat(a.getGuestEmail()).isNull();
  }

  @Test
  void appointmentsCreatedByThePractitionerHavePractitionerOrigin() {
    assertThat(guest(null).getOrigin()).isEqualTo(AppointmentOrigin.PRACTITIONER);
  }

  @Test
  void originIsStoredWithItsSqlValue() {
    AppointmentOriginConverter converter = new AppointmentOriginConverter();
    assertThat(converter.convertToDatabaseColumn(AppointmentOrigin.PRACTITIONER))
        .isEqualTo("practitioner");
    assertThat(converter.convertToDatabaseColumn(AppointmentOrigin.ONLINE)).isEqualTo("online");
    assertThat(converter.convertToEntityAttribute("online")).isEqualTo(AppointmentOrigin.ONLINE);
    assertThat(converter.convertToEntityAttribute(null)).isNull();
    assertThatThrownBy(() -> converter.convertToEntityAttribute("google"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
