package com.psymate.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.agenda.api.AppointmentCancelled;
import com.psymate.agenda.api.AppointmentConfirmed;
import com.psymate.agenda.api.AppointmentPlanned;
import com.psymate.agenda.api.AppointmentRescheduled;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Cycle de vie d'un rendez-vous (M11-05, docs/I-plan-agenda-api.md I5) sur une vraie base
 * (Testcontainers pgvector/pgvector:pg16) : un cas par critère d'acceptation, deux praticiens du
 * même cabinet (Alice, propriétaire des patients, et Bob).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, AppointmentIT.ListenerConfig.class})
class AppointmentIT {

  /**
   * Abonnés de test : le registre Modulith n'écrit une publication dans {@code event_publication}
   * que s'il existe au moins un abonné ; l'agenda n'en a aucun en V1 (G Q59).
   */
  static class AppointmentEventsListener {
    @ApplicationModuleListener
    void on(AppointmentPlanned event) {}

    @ApplicationModuleListener
    void on(AppointmentRescheduled event) {}

    @ApplicationModuleListener
    void on(AppointmentCancelled event) {}

    @ApplicationModuleListener
    void on(AppointmentConfirmed event) {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ListenerConfig {
    @Bean
    AppointmentEventsListener appointmentEventsListener() {
      return new AppointmentEventsListener();
    }
  }

  private static final String MONDAY_NINE = "2026-11-16T09:00:00Z";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;
  private UUID alice;
  private UUID bob;
  private UUID theo;
  private UUID bobsPatient;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet agenda')", tenant);
    alice = practitioner();
    bob = practitioner();
    theo = patient(alice, "Théo B.");
    bobsPatient = patient(bob, "Patient de Bob");
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- création

  @Test
  void createReturns201WithLocationRowOfCurrentPractitionerAndPlannedEvent() throws Exception {
    ResultActions result =
        post(alice, "/api/appointments", create(theo, MONDAY_NINE, 50, "video"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.appointments.length()").value(1))
            .andExpect(jsonPath("$.conflicts.length()").value(0))
            .andExpect(jsonPath("$.appointments[0].patient.id").value(theo.toString()))
            .andExpect(jsonPath("$.appointments[0].patient.displayName").value("Théo B."))
            .andExpect(jsonPath("$.appointments[0].startsAt").value(MONDAY_NINE))
            .andExpect(jsonPath("$.appointments[0].endsAt").value("2026-11-16T09:50:00Z"))
            .andExpect(jsonPath("$.appointments[0].durationMin").value(50))
            .andExpect(jsonPath("$.appointments[0].modality").value("video"))
            .andExpect(jsonPath("$.appointments[0].status").value("planned"));
    String id = JsonPath.read(body(result), "$.appointments[0].id");
    result.andExpect(header().string("Location", "/api/appointments/" + id));

    Map<String, Object> row =
        jdbc.queryForMap(
            "select tenant_id, practitioner_id, patient_id, status from appointment where id = ?",
            UUID.fromString(id));
    assertThat(row.get("tenant_id")).isEqualTo(tenant);
    assertThat(row.get("practitioner_id")).isEqualTo(alice);
    assertThat(row.get("patient_id")).isEqualTo(theo);
    assertThat(row.get("status")).isEqualTo("planned");
    assertThat(publications(AppointmentPlanned.class, id)).isEqualTo(1);
  }

  @Test
  void createWithTentativeIsTentative() throws Exception {
    post(alice, "/api/appointments", createTentative(theo, MONDAY_NINE))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appointments[0].status").value("tentative"));
  }

  @Test
  void createForAnotherPractitionersPatientIs403PatientForbidden() throws Exception {
    post(alice, "/api/appointments", create(bobsPatient, MONDAY_NINE, 50, "in_person"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    assertThat(appointmentsOf(alice)).isZero();
  }

  @Test
  void createForArchivedPatientIs422PatientArchived() throws Exception {
    jdbc.update("update patient set archived_at = now() where id = ?", theo);
    post(alice, "/api/appointments", create(theo, MONDAY_NINE, 50, "in_person"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"))
        .andExpect(jsonPath("$.errors[0].field").value("patientId"));
    assertThat(appointmentsOf(alice)).isZero();
  }

  @Test
  void durationMin3Is422OutOfRange() throws Exception {
    post(alice, "/api/appointments", create(theo, MONDAY_NINE, 3, "in_person"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("durationMin"))
        .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));
  }

  @Test
  void modalityEmailIs422InvalidValue() throws Exception {
    post(alice, "/api/appointments", create(theo, MONDAY_NINE, 50, "email"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("modality"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
  }

  @Test
  void missingStartsAtIs422Required() throws Exception {
    post(
            alice,
            "/api/appointments",
            "{\"patientId\":\"" + theo + "\",\"durationMin\":50,\"modality\":\"phone\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("startsAt"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
  }

  @Test
  void recurrenceIs422UnknownField() throws Exception {
    post(
            alice,
            "/api/appointments",
            withField(
                create(theo, MONDAY_NINE, 50, "in_person"),
                "\"recurrence\":{\"everyWeeks\":1,\"count\":4}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("recurrence"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    assertThat(appointmentsOf(alice)).isZero();
  }

  @Test
  void statusIs422UnknownField() throws Exception {
    post(
            alice,
            "/api/appointments",
            withField(create(theo, MONDAY_NINE, 50, "in_person"), "\"status\":\"done\""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("status"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));
  }

  @Test
  void serverFieldsAreAllRefusedWithUnknownField() throws Exception {
    for (String field :
        List.of(
            "\"authorType\":\"practitioner\"",
            "\"submissionId\":\"" + UUID.randomUUID() + "\"",
            "\"practitionerId\":\"" + bob + "\"",
            "\"tenantId\":\"" + UUID.randomUUID() + "\"",
            "\"sessionEntryId\":\"" + UUID.randomUUID() + "\"")) {
      post(alice, "/api/appointments", withField(create(theo, MONDAY_NINE, 50, "phone"), field))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));
    }
    String id = createAppointment(alice, theo, MONDAY_NINE, 50);
    patch(alice, id, "{\"status\":\"cancelled\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("status"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));
    patch(alice, id, "{\"patientId\":\"" + UUID.randomUUID() + "\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("patientId"));
    assertThat(appointmentsOf(alice)).isEqualTo(1);
  }

  // ---- chevauchements (G Q54)

  @Test
  void overlappingCreateIs201WithTheExistingAppointmentInConflicts() throws Exception {
    String existing = createAppointment(alice, theo, MONDAY_NINE, 50);
    UUID other = patient(alice, "Léa M.");
    post(alice, "/api/appointments", create(other, "2026-11-16T09:30:00Z", 50, "in_person"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.conflicts.length()").value(1))
        .andExpect(jsonPath("$.conflicts[0].appointmentId").value(existing))
        .andExpect(jsonPath("$.conflicts[0].startsAt").value(MONDAY_NINE))
        .andExpect(jsonPath("$.conflicts[0].endsAt").value("2026-11-16T09:50:00Z"))
        .andExpect(jsonPath("$.conflicts[0].patientDisplayName").value("Théo B."));
  }

  @Test
  void cancelledAppointmentDoesNotCreateAConflict() throws Exception {
    String existing = createAppointment(alice, theo, MONDAY_NINE, 50);
    post(alice, "/api/appointments/" + existing + "/cancel", null).andExpect(status().isOk());
    post(alice, "/api/appointments", create(theo, "2026-11-16T09:30:00Z", 50, "in_person"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.conflicts.length()").value(0));
  }

  @Test
  void appointmentEndingAtTenDoesNotConflictWithOneStartingAtTen() throws Exception {
    createAppointment(alice, theo, MONDAY_NINE, 60);
    post(alice, "/api/appointments", create(theo, "2026-11-16T10:00:00Z", 50, "in_person"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.conflicts.length()").value(0));
  }

  @Test
  void anotherPractitionersAppointmentIsNeverAConflict() throws Exception {
    createAppointment(bob, bobsPatient, MONDAY_NINE, 50);
    post(alice, "/api/appointments", create(theo, MONDAY_NINE, 50, "in_person"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.conflicts.length()").value(0));
  }

  // ---- lecture

  @Test
  void getOfAnotherPractitionerIs404() throws Exception {
    String id = createAppointment(alice, theo, MONDAY_NINE, 50);
    get(bob, "/api/appointments/" + id)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
    patch(bob, id, "{\"durationMin\":30}").andExpect(status().isNotFound());
    post(bob, "/api/appointments/" + id + "/cancel", "{}").andExpect(status().isNotFound());
    post(bob, "/api/appointments/" + id + "/confirm", null).andExpect(status().isNotFound());
  }

  @Test
  void getOfThePractitionerIsTheCompleteDto() throws Exception {
    jdbc.update("update patient set birth_date = ? where id = ?", LocalDate.of(1990, 4, 2), theo);
    String id =
        idOf(
            post(
                    alice,
                    "/api/appointments",
                    "{\"patientId\":\""
                        + theo
                        + "\",\"startsAt\":\""
                        + MONDAY_NINE
                        + "\",\"durationMin\":45,\"modality\":\"phone\","
                        + "\"planningNote\":\"Apporter le bilan\"}")
                .andExpect(status().isCreated()));
    get(alice, "/api/appointments/" + id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.patient.id").value(theo.toString()))
        .andExpect(jsonPath("$.patient.displayName").value("Théo B."))
        .andExpect(jsonPath("$.patient.birthDate").value("1990-04-02"))
        .andExpect(jsonPath("$.patient.archived").value(false))
        .andExpect(jsonPath("$.seriesId").value(nullValue()))
        .andExpect(jsonPath("$.startsAt").value(MONDAY_NINE))
        .andExpect(jsonPath("$.endsAt").value("2026-11-16T09:45:00Z"))
        .andExpect(jsonPath("$.durationMin").value(45))
        .andExpect(jsonPath("$.modality").value("phone"))
        .andExpect(jsonPath("$.status").value("planned"))
        .andExpect(jsonPath("$.planningNote").value("Apporter le bilan"))
        .andExpect(jsonPath("$.cancelReason").value(nullValue()))
        .andExpect(jsonPath("$.cancelledAt").value(nullValue()))
        .andExpect(jsonPath("$.sessionEntryId").value(nullValue()))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.updatedAt").isNotEmpty());
  }

  // ---- modification

  @Test
  void patchStartsAtRecomputesEndsAtWithTheSameDurationAndPublishesRescheduled() throws Exception {
    String id = createAppointment(alice, theo, MONDAY_NINE, 50);
    patch(alice, id, "{\"startsAt\":\"2026-11-17T14:00:00Z\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.startsAt").value("2026-11-17T14:00:00Z"))
        .andExpect(jsonPath("$.appointment.endsAt").value("2026-11-17T14:50:00Z"))
        .andExpect(jsonPath("$.appointment.durationMin").value(50))
        .andExpect(jsonPath("$.conflicts.length()").value(0));
    OffsetDateTime endsAt =
        jdbc.queryForObject(
            "select ends_at from appointment where id = ?",
            OffsetDateTime.class,
            UUID.fromString(id));
    assertThat(endsAt.toInstant())
        .isEqualTo(OffsetDateTime.parse("2026-11-17T14:50:00Z").toInstant());
    assertThat(publications(AppointmentRescheduled.class, id)).isEqualTo(1);
  }

  @Test
  void patchDurationKeepsTheStartAndReportsConflicts() throws Exception {
    String id = createAppointment(alice, theo, MONDAY_NINE, 50);
    String next = createAppointment(alice, theo, "2026-11-16T10:00:00Z", 50);
    patch(alice, id, "{\"durationMin\":90}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.startsAt").value(MONDAY_NINE))
        .andExpect(jsonPath("$.appointment.endsAt").value("2026-11-16T10:30:00Z"))
        .andExpect(jsonPath("$.conflicts.length()").value(1))
        .andExpect(jsonPath("$.conflicts[0].appointmentId").value(next));
    assertThat(publications(AppointmentRescheduled.class, id)).isEqualTo(1);
  }

  @Test
  void patchNullPlanningNoteClearsItWithoutRescheduled() throws Exception {
    String id =
        idOf(
            post(
                    alice,
                    "/api/appointments",
                    "{\"patientId\":\""
                        + theo
                        + "\",\"startsAt\":\""
                        + MONDAY_NINE
                        + "\",\"durationMin\":50,\"modality\":\"in_person\","
                        + "\"planningNote\":\"Note\"}")
                .andExpect(status().isCreated()));
    // Un champ absent ne change rien : la note reste.
    patch(alice, id, "{\"modality\":\"video\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.modality").value("video"))
        .andExpect(jsonPath("$.appointment.planningNote").value("Note"));
    patch(alice, id, "{\"planningNote\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.planningNote").value(nullValue()))
        .andExpect(jsonPath("$.appointment.startsAt").value(MONDAY_NINE));
    assertThat(
            jdbc.queryForObject(
                "select planning_note from appointment where id = ?",
                String.class,
                UUID.fromString(id)))
        .isNull();
    assertThat(publications(AppointmentRescheduled.class, id)).isZero();
  }

  @Test
  void patchOnCancelledIs409AppointmentNotActive() throws Exception {
    String id = createAppointment(alice, theo, MONDAY_NINE, 50);
    post(alice, "/api/appointments/" + id + "/cancel", "{}").andExpect(status().isOk());
    patch(alice, id, "{\"startsAt\":\"2026-11-17T14:00:00Z\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_ACTIVE"));
    patch(alice, id, "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_ACTIVE"));
  }

  // ---- annulation

  @Test
  void cancelSetsCancelledAndIsIdempotent() throws Exception {
    String id = createAppointment(alice, theo, MONDAY_NINE, 50);
    String first =
        body(
            post(alice, "/api/appointments/" + id + "/cancel", "{\"reason\":\"Patient malade\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.cancelReason").value("Patient malade"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty()));
    Map<String, Object> row =
        jdbc.queryForMap(
            "select status, cancelled_at, cancel_reason from appointment where id = ?",
            UUID.fromString(id));
    assertThat(row.get("status")).isEqualTo("cancelled");
    assertThat(row.get("cancelled_at")).isNotNull();
    assertThat(row.get("cancel_reason")).isEqualTo("Patient malade");

    String second =
        body(
            post(alice, "/api/appointments/" + id + "/cancel", "{\"reason\":\"Autre\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelReason").value("Patient malade")));
    assertThat(second).isEqualTo(first);
    assertThat(publications(AppointmentCancelled.class, id)).isEqualTo(1);
  }

  @Test
  void cancelWithoutBodyIsAccepted() throws Exception {
    String id = createAppointment(alice, theo, MONDAY_NINE, 50);
    post(alice, "/api/appointments/" + id + "/cancel", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.cancelReason").value(nullValue()));
  }

  @Test
  void cancelOnDoneIs409() throws Exception {
    String id = createAppointment(alice, theo, MONDAY_NINE, 50);
    jdbc.update("update appointment set status = 'done' where id = ?", UUID.fromString(id));
    post(alice, "/api/appointments/" + id + "/cancel", "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_ACTIVE"));
    assertThat(
            jdbc.queryForObject(
                "select status from appointment where id = ?", String.class, UUID.fromString(id)))
        .isEqualTo("done");
  }

  // ---- confirmation

  @Test
  void confirmTurnsTentativeIntoPlannedThenPlannedIs409() throws Exception {
    String id = idOf(post(alice, "/api/appointments", createTentative(theo, MONDAY_NINE)));
    post(alice, "/api/appointments/" + id + "/confirm", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("planned"));
    assertThat(publications(AppointmentConfirmed.class, id)).isEqualTo(1);
    post(alice, "/api/appointments/" + id + "/confirm", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_ACTIVE"));
  }

  // ---- aucune suppression (G Q53)

  @Test
  void noAppointmentRowIsEverDeleted() throws Exception {
    String a = createAppointment(alice, theo, MONDAY_NINE, 50);
    String b =
        idOf(post(alice, "/api/appointments", createTentative(theo, "2026-11-16T11:00:00Z")));
    String c = createAppointment(alice, theo, "2026-11-16T14:00:00Z", 50);
    int before = appointmentsOf(alice);
    assertThat(before).isEqualTo(3);

    patch(alice, a, "{\"startsAt\":\"2026-11-18T09:00:00Z\",\"planningNote\":null}")
        .andExpect(status().isOk());
    post(alice, "/api/appointments/" + b + "/confirm", null).andExpect(status().isOk());
    post(alice, "/api/appointments/" + c + "/cancel", "{}").andExpect(status().isOk());
    post(alice, "/api/appointments/" + c + "/cancel", "{}").andExpect(status().isOk());
    get(alice, "/api/appointments/" + c)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"));

    assertThat(appointmentsOf(alice)).isEqualTo(before);
  }

  // ---- outils

  private UUID practitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Agenda', 'Dr Agenda', 'psychologist')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  private UUID patient(UUID owner, String displayName) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, ?)",
        id,
        tenant,
        owner,
        displayName);
    return id;
  }

  private RequestPostProcessor as(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            tenant,
            practitionerId + "@example.test",
            "Dr Agenda",
            "psychologist",
            "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions send(MockHttpServletRequestBuilder builder, UUID who, String body)
      throws Exception {
    if (body != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.getBytes(StandardCharsets.UTF_8));
    }
    return mvc.perform(builder.with(as(who)));
  }

  private ResultActions post(UUID who, String url, String body) throws Exception {
    return send(MockMvcRequestBuilders.post(url), who, body);
  }

  private ResultActions patch(UUID who, String id, String body) throws Exception {
    return send(MockMvcRequestBuilders.patch("/api/appointments/" + id), who, body);
  }

  private ResultActions get(UUID who, String url) throws Exception {
    return send(MockMvcRequestBuilders.get(url), who, null);
  }

  private static String create(UUID patientId, String startsAt, int durationMin, String modality) {
    return "{\"patientId\":\""
        + patientId
        + "\",\"startsAt\":\""
        + startsAt
        + "\",\"durationMin\":"
        + durationMin
        + ",\"modality\":\""
        + modality
        + "\"}";
  }

  private static String createTentative(UUID patientId, String startsAt) {
    return withField(create(patientId, startsAt, 50, "in_person"), "\"tentative\":true");
  }

  /** Ajoute une paire clé-valeur JSON à la fin d'un objet. */
  private static String withField(String json, String pair) {
    return json.substring(0, json.length() - 1) + "," + pair + "}";
  }

  private String createAppointment(UUID who, UUID patientId, String startsAt, int durationMin)
      throws Exception {
    return idOf(
        post(who, "/api/appointments", create(patientId, startsAt, durationMin, "in_person"))
            .andExpect(status().isCreated()));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private static String idOf(ResultActions result) throws Exception {
    return JsonPath.read(body(result), "$.appointments[0].id");
  }

  private int appointmentsOf(UUID practitionerId) {
    return jdbc.queryForObject(
        "select count(*) from appointment where practitioner_id = ?",
        Integer.class,
        practitionerId);
  }

  /** Publications du registre pour notre abonné, pour un type d'événement et un rendez-vous. */
  private int publications(Class<?> eventType, String appointmentId) {
    return jdbc.queryForObject(
        "select count(*) from event_publication where listener_id like '%AppointmentIT%'"
            + " and event_type = ? and serialized_event::text like ?",
        Integer.class, eventType.getName(), "%" + appointmentId + "%");
  }
}
