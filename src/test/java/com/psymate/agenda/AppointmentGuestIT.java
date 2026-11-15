package com.psymate.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.agenda.api.AppointmentPatientAttached;
import com.psymate.agenda.api.AppointmentPlanned;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
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
 * Rendez-vous invité (M11-10, G Q60, docs/I-plan-agenda-api.md I8) sur une vraie base
 * (Testcontainers pgvector/pgvector:pg16) : un cas par critère d'acceptation, deux praticiens du
 * même cabinet (Alice et Bob). La purge est couverte par {@code GuestPurgeJobIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, AppointmentGuestIT.ListenerConfig.class})
class AppointmentGuestIT {

  /** Abonnés de test : sans abonné, le registre Modulith n'écrit aucune publication. */
  static class GuestEventsListener {
    @ApplicationModuleListener
    void on(AppointmentPlanned event) {}

    @ApplicationModuleListener
    void on(AppointmentPatientAttached event) {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ListenerConfig {
    @Bean
    GuestEventsListener guestEventsListener() {
      return new GuestEventsListener();
    }
  }

  private static final String SLOT = "2031-03-10T09:00:00Z";
  private static final String SARA =
      "{\"lastName\":\"Benali\",\"firstName\":\"Sara\",\"phone\":\"+33 6 12 34 56 78\"}";

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
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet invités')", tenant);
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
  void createGuestReturns201WithGuestColumnsNoPatientAndPlannedEvent() throws Exception {
    ResultActions result =
        post(alice, "/api/appointments", guestCreate(SARA, SLOT))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.appointments.length()").value(1))
            .andExpect(jsonPath("$.appointments[0].patient").value(nullValue()))
            .andExpect(jsonPath("$.appointments[0].guest.lastName").value("Benali"))
            .andExpect(jsonPath("$.appointments[0].guest.firstName").value("Sara"))
            .andExpect(jsonPath("$.appointments[0].guest.phone").value("+33 6 12 34 56 78"))
            .andExpect(jsonPath("$.appointments[0].guest.purgedAt").value(nullValue()));
    String id = idOf(result);

    Map<String, Object> row = row(id);
    assertThat(row.get("patient_id")).isNull();
    assertThat(row.get("guest_last_name")).isEqualTo("Benali");
    assertThat(row.get("guest_first_name")).isEqualTo("Sara");
    assertThat(row.get("guest_phone")).isEqualTo("+33 6 12 34 56 78");
    assertThat(row.get("guest_purged_at")).isNull();
    assertThat(row.get("practitioner_id")).isEqualTo(alice);
    assertThat(publications(AppointmentPlanned.class, id)).isEqualTo(1);
  }

  @Test
  void createWithoutPatientIdNorGuestIs422RequiredOnPatientId() throws Exception {
    post(alice, "/api/appointments", base(SLOT) + "}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("patientId"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
  }

  @Test
  void createWithPatientIdAndGuestIs422InvalidValueOnGuest() throws Exception {
    post(
            alice,
            "/api/appointments",
            base(SLOT) + ",\"patientId\":\"" + theo + "\",\"guest\":" + SARA + "}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("guest"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    assertThat(appointmentsOf(alice)).isZero();
  }

  @Test
  void guestWithoutLastNameIs422RequiredOnGuestLastName() throws Exception {
    post(alice, "/api/appointments", guestCreate("{\"firstName\":\"Sara\"}", SLOT))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("guest.lastName"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
  }

  @Test
  void guestPhoneAbcIs422InvalidFormatOnGuestPhone() throws Exception {
    post(
            alice,
            "/api/appointments",
            guestCreate("{\"lastName\":\"Benali\",\"phone\":\"abc\"}", SLOT))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("guest.phone"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
  }

  @Test
  void guestSeriesCreatesThreeGuestRowsWithSameSeriesAndSameGuest() throws Exception {
    String body =
        guestCreate(SARA, SLOT)
            .replaceFirst("}$", ",\"recurrence\":{\"everyWeeks\":1,\"count\":3}}");
    post(alice, "/api/appointments", body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appointments.length()").value(3));
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select series_id, patient_id, guest_first_name, guest_last_name, guest_phone"
                + " from appointment where practitioner_id = ? order by starts_at",
            alice);
    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).get("series_id")).isNotNull();
    for (Map<String, Object> row : rows) {
      assertThat(row.get("series_id")).isEqualTo(rows.get(0).get("series_id"));
      assertThat(row.get("patient_id")).isNull();
      assertThat(row.get("guest_first_name")).isEqualTo("Sara");
      assertThat(row.get("guest_last_name")).isEqualTo("Benali");
      assertThat(row.get("guest_phone")).isEqualTo("+33 6 12 34 56 78");
    }
  }

  // ---- lectures

  @Test
  void agendaWindowReturnsGuestAppointmentWithGuest() throws Exception {
    String id = createGuest(alice, SARA, SLOT);
    get(alice, "/api/agenda?from=2031-03-09T00:00:00Z&to=2031-03-16T00:00:00Z")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments.length()").value(1))
        .andExpect(jsonPath("$.appointments[0].id").value(id))
        .andExpect(jsonPath("$.appointments[0].patient").value(nullValue()))
        .andExpect(jsonPath("$.appointments[0].guest.lastName").value("Benali"));
  }

  @Test
  void freebusyConflictWithGuestShowsFirstAndLastName() throws Exception {
    String id = createGuest(alice, SARA, SLOT);
    get(alice, "/api/agenda/freebusy?from=2031-03-10T09:30:00Z&to=2031-03-10T10:20:00Z")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conflicts.length()").value(1))
        .andExpect(jsonPath("$.conflicts[0].appointmentId").value(id))
        .andExpect(jsonPath("$.conflicts[0].patientDisplayName").value("Sara Benali"));
  }

  @Test
  void createOverlappingAGuestReportsGuestNameInConflicts() throws Exception {
    createGuest(alice, SARA, SLOT);
    post(alice, "/api/appointments", patientCreate(theo, "2031-03-10T09:30:00Z"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.conflicts[0].patientDisplayName").value("Sara Benali"));
  }

  @Test
  void patientAppointmentsNeverListGuests() throws Exception {
    createGuest(alice, SARA, SLOT);
    String patientsOwn = idOf(post(alice, "/api/appointments", patientCreate(theo, SLOT)));
    get(alice, "/api/patients/" + theo + "/appointments?from=2031-01-01T00:00:00Z")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments.length()").value(1))
        .andExpect(jsonPath("$.appointments[0].id").value(patientsOwn));
  }

  // ---- correction (PATCH)

  @Test
  void patchGuestCorrectsTheName() throws Exception {
    String id = createGuest(alice, SARA, SLOT);
    patch(alice, id, "{\"guest\":{\"lastName\":\"Ben Ali\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.guest.lastName").value("Ben Ali"))
        .andExpect(jsonPath("$.appointment.guest.firstName").value("Sara"))
        .andExpect(jsonPath("$.appointment.guest.phone").value("+33 6 12 34 56 78"));
    assertThat(row(id).get("guest_last_name")).isEqualTo("Ben Ali");
    assertThat(row(id).get("guest_first_name")).isEqualTo("Sara");
  }

  @Test
  void patchGuestWithEmptyPhoneErasesItAndKeepsTheRest() throws Exception {
    String id = createGuest(alice, SARA, SLOT);
    patch(alice, id, "{\"guest\":{\"lastName\":\"Benali\",\"phone\":\"\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.guest.phone").value(nullValue()))
        .andExpect(jsonPath("$.appointment.guest.firstName").value("Sara"));
  }

  @Test
  void patchGuestOnPatientAppointmentIs422InvalidValue() throws Exception {
    String id = idOf(post(alice, "/api/appointments", patientCreate(theo, SLOT)));
    patch(alice, id, "{\"guest\":{\"lastName\":\"Ben Ali\"}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("guest"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    assertThat(row(id).get("guest_last_name")).isNull();
  }

  // ---- rattachement

  @Test
  void attachPatientSetsPatientClearsGuestAndPublishesEvent() throws Exception {
    String id = createGuest(alice, SARA, SLOT);
    post(alice, attach(id), "{\"patientId\":\"" + theo + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments.length()").value(1))
        .andExpect(jsonPath("$.appointments[0].id").value(id))
        .andExpect(jsonPath("$.appointments[0].patient.id").value(theo.toString()))
        .andExpect(jsonPath("$.appointments[0].patient.displayName").value("Théo B."))
        .andExpect(jsonPath("$.appointments[0].guest").value(nullValue()));
    Map<String, Object> row = row(id);
    assertThat(row.get("patient_id")).isEqualTo(theo);
    assertThat(row.get("guest_first_name")).isNull();
    assertThat(row.get("guest_last_name")).isNull();
    assertThat(row.get("guest_phone")).isNull();
    assertThat(row.get("guest_purged_at")).isNull();
    assertThat(publications(AppointmentPatientAttached.class, id)).isEqualTo(1);
  }

  @Test
  void attachWithIncludeSameGuestAttachesSameGuestOnlyIgnoringCaseAndSpaces() throws Exception {
    String first = createGuest(alice, SARA, SLOT);
    String second = createGuest(alice, SARA, "2031-03-17T09:00:00Z");
    String third =
        createGuest(
            alice,
            "{\"lastName\":\" benali \",\"firstName\":\"sara \",\"phone\":\"+33612345678\"}",
            "2031-03-24T09:00:00Z");
    String other =
        createGuest(
            alice,
            "{\"lastName\":\"Benali\",\"firstName\":\"Yanis\",\"phone\":\"+33 6 12 34 56 78\"}",
            "2031-03-31T09:00:00Z");
    // Le même invité chez Bob n'est jamais touché.
    String bobs = createGuest(bob, SARA, SLOT);

    post(alice, attach(first), "{\"patientId\":\"" + theo + "\",\"includeSameGuest\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments.length()").value(3))
        .andExpect(jsonPath("$.appointments[0].id").value(first))
        .andExpect(jsonPath("$.appointments[1].id").value(second))
        .andExpect(jsonPath("$.appointments[2].id").value(third));
    for (String id : List.of(first, second, third)) {
      assertThat(row(id).get("patient_id")).as(id).isEqualTo(theo);
      assertThat(row(id).get("guest_last_name")).as(id).isNull();
      assertThat(publications(AppointmentPatientAttached.class, id)).isEqualTo(1);
    }
    assertThat(row(other).get("patient_id")).isNull();
    assertThat(row(other).get("guest_first_name")).isEqualTo("Yanis");
    assertThat(row(bobs).get("patient_id")).isNull();
    assertThat(row(bobs).get("guest_last_name")).isEqualTo("Benali");
  }

  @Test
  void attachOnPatientAppointmentIs409() throws Exception {
    String id = idOf(post(alice, "/api/appointments", patientCreate(theo, SLOT)));
    post(alice, attach(id), "{\"patientId\":\"" + theo + "\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_ACTIVE"));
  }

  @Test
  void attachToAnotherPractitionersPatientIs403() throws Exception {
    String id = createGuest(alice, SARA, SLOT);
    post(alice, attach(id), "{\"patientId\":\"" + bobsPatient + "\"}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    assertThat(row(id).get("patient_id")).isNull();
  }

  @Test
  void attachToArchivedPatientIs422PatientArchived() throws Exception {
    String id = createGuest(alice, SARA, SLOT);
    jdbc.update("update patient set archived_at = now() where id = ?", theo);
    post(alice, attach(id), "{\"patientId\":\"" + theo + "\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"))
        .andExpect(jsonPath("$.errors[0].field").value("patientId"));
    assertThat(row(id).get("patient_id")).isNull();
  }

  @Test
  void attachWithoutPatientIdIs422Required() throws Exception {
    String id = createGuest(alice, SARA, SLOT);
    post(alice, attach(id), "{}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("patientId"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
  }

  // ---- portée (G Q58)

  @Test
  void anotherPractitionersGuestAppointmentIs404ForAttachPatchAndGet() throws Exception {
    String id = createGuest(alice, SARA, SLOT);
    UUID bobsOwn = patient(bob, "Autre patient de Bob");
    post(bob, attach(id), "{\"patientId\":\"" + bobsOwn + "\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
    patch(bob, id, "{\"guest\":{\"lastName\":\"Ben Ali\"}}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
    get(bob, "/api/appointments/" + id)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
    assertThat(row(id).get("guest_last_name")).isEqualTo("Benali");
    assertThat(row(id).get("patient_id")).isNull();
  }

  // ---- aucune suppression

  @Test
  void noAppointmentRowIsEverDeleted() throws Exception {
    String a = createGuest(alice, SARA, SLOT);
    String b = createGuest(alice, SARA, "2031-03-17T09:00:00Z");
    createGuest(alice, "{\"lastName\":\"Autre\"}", "2031-03-24T09:00:00Z");
    int before = appointmentsOf(alice);
    assertThat(before).isEqualTo(3);

    patch(alice, a, "{\"guest\":{\"lastName\":\"Benali\",\"firstName\":\"Sara\"}}")
        .andExpect(status().isOk());
    post(alice, "/api/appointments/" + b + "/cancel", "{}").andExpect(status().isOk());
    post(alice, attach(a), "{\"patientId\":\"" + theo + "\",\"includeSameGuest\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments.length()").value(2));

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

  private static String attach(String id) {
    return "/api/appointments/" + id + "/attach-patient";
  }

  /** Début d'un corps de création sans sujet, objet JSON non fermé. */
  private static String base(String startsAt) {
    return "{\"startsAt\":\"" + startsAt + "\",\"durationMin\":50,\"modality\":\"in_person\"";
  }

  private static String guestCreate(String guest, String startsAt) {
    return base(startsAt) + ",\"guest\":" + guest + "}";
  }

  private static String patientCreate(UUID patientId, String startsAt) {
    return base(startsAt) + ",\"patientId\":\"" + patientId + "\"}";
  }

  private String createGuest(UUID who, String guest, String startsAt) throws Exception {
    return idOf(
        post(who, "/api/appointments", guestCreate(guest, startsAt))
            .andExpect(status().isCreated()));
  }

  private static String idOf(ResultActions result) throws Exception {
    String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return JsonPath.read(body, "$.appointments[0].id");
  }

  private Map<String, Object> row(String id) {
    return jdbc.queryForMap(
        "select practitioner_id, patient_id, guest_first_name, guest_last_name, guest_phone,"
            + " guest_purged_at from appointment where id = ?",
        UUID.fromString(id));
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
        "select count(*) from event_publication where listener_id like '%AppointmentGuestIT%'"
            + " and event_type = ? and serialized_event::text like ?",
        Integer.class, eventType.getName(), "%" + appointmentId + "%");
  }
}
