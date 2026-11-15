package com.psymate.patients.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.testsupport.SharedPostgres;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link PatientFacade#followUp} (M11-03, G Q56) : le cadre du suivi lu dans {@code
 * patient.follow_up} tel que l'agenda en a besoin, sur base réelle. La façade est obtenue par son
 * interface, comme le ferait un autre module.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class PatientFacadeIT {

  @Autowired private PatientFacade facade;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;
  private UUID owner;

  @BeforeEach
  void data() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    owner = UUID.randomUUID();
    String email = owner + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist')",
        owner,
        tenant,
        email,
        email);
  }

  private UUID patientWithFollowUp(String json) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name, follow_up)"
            + " values (?, ?, ?, 'Théo B.', ?::jsonb)",
        id,
        tenant,
        owner,
        json);
    return id;
  }

  @Test
  void completeFollowUpGivesTheFourFieldsAndNeitherFeeNorPrescriber() {
    UUID id =
        patientWithFollowUp(
            "{\"since\":\"2026-01-15\",\"rhythm\":\"toutes les 1 à 2 semaines\","
                + "\"modality\":\"visio\",\"durationMin\":45,"
                + "\"fee\":70,\"prescriber\":\"Dr Martin\"}");

    assertThat(facade.followUp(id))
        .contains(
            new FollowUpView(LocalDate.of(2026, 1, 15), "toutes les 1 à 2 semaines", "visio", 45));
  }

  @Test
  void emptyFollowUpIsPresentWithFourNulls() {
    UUID id = patientWithFollowUp("{}");

    Optional<FollowUpView> view = facade.followUp(id);

    assertThat(view).isPresent();
    assertThat(view.get()).isEqualTo(new FollowUpView(null, null, null, null));
  }

  @Test
  void unknownPatientIsEmpty() {
    assertThat(facade.followUp(UUID.randomUUID())).isEmpty();
  }

  @Test
  void feeAndPrescriberAreNotComponentsOfTheView() {
    assertThat(FollowUpView.class.getRecordComponents())
        .extracting(RecordComponent::getName)
        .containsExactly("since", "rhythm", "modality", "durationMin");
  }

  /**
   * Une durée qui n'est pas un entier entre 1 et 600 vaut null, sans exception ; le reste est lu.
   */
  @ParameterizedTest
  @ValueSource(strings = {"\"soixante\"", "900", "0", "-30", "45.5", "true", "null"})
  void durationMinOutsideOneToSixHundredOrNotAnIntegerIsNull(String durationMin) {
    UUID id =
        patientWithFollowUp("{\"rhythm\":\"hebdomadaire\",\"durationMin\":" + durationMin + "}");

    assertThat(facade.followUp(id)).contains(new FollowUpView(null, "hebdomadaire", null, null));
  }

  @Test
  void boundsOfTheDurationAreAccepted() {
    assertThat(facade.followUp(patientWithFollowUp("{\"durationMin\":1}")).get().durationMin())
        .isEqualTo(1);
    assertThat(facade.followUp(patientWithFollowUp("{\"durationMin\":600}")).get().durationMin())
        .isEqualTo(600);
  }

  @Test
  void unreadableSinceOrNonTextualFieldsAreNull() {
    UUID id =
        patientWithFollowUp(
            "{\"since\":\"janvier 2026\",\"rhythm\":3,\"modality\":\"\",\"durationMin\":60}");

    assertThat(facade.followUp(id)).contains(new FollowUpView(null, null, null, 60));
  }
}
