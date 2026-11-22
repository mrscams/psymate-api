package com.psymate.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * E-mail de l'invité et origine d'un rendez-vous (M14-04, G Q74, Q82, V15) sur une vraie base
 * (Testcontainers pgvector/pgvector:pg16) : un cas par critère d'acceptation de {@code guest.email}
 * et de {@code origin}. La liste des demandes est couverte par {@code AgendaRequestsIT}, la purge
 * par {@code GuestPurgeJobIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class AppointmentGuestEmailIT {

  private static final String SLOT = "2031-04-07T09:00:00Z";
  private static final String SARA =
      "{\"lastName\":\"Benali\",\"firstName\":\"Sara\",\"email\":\"sara@example.test\"}";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;
  private UUID alice;
  private UUID theo;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet e-mail invité')", tenant);
    alice = UUID.randomUUID();
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Agenda', 'Dr Agenda', 'psychologist')",
        alice,
        tenant,
        alice + "@example.test",
        alice + "@example.test");
    theo = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'Théo B.')",
        theo,
        tenant,
        alice);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- création

  @Test
  void createGuestWithEmailReturns201WithEmailAndPractitionerOrigin() throws Exception {
    ResultActions result =
        post(
                "/api/appointments",
                guestCreate("{\"lastName\":\"Benali\",\"email\":\"sara@example.test\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.appointments[0].guest.lastName").value("Benali"))
            .andExpect(jsonPath("$.appointments[0].guest.email").value("sara@example.test"))
            .andExpect(jsonPath("$.appointments[0].origin").value("practitioner"));
    String id = idOf(result);

    Map<String, Object> row = row(id);
    assertThat(row.get("guest_email")).isEqualTo("sara@example.test");
    assertThat(row.get("origin")).isEqualTo("practitioner");
    get("/api/appointments/" + id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.guest.email").value("sara@example.test"))
        .andExpect(jsonPath("$.origin").value("practitioner"));
  }

  @Test
  void createGuestWithoutEmailHasNullEmail() throws Exception {
    String id =
        idOf(
            post("/api/appointments", guestCreate("{\"lastName\":\"Benali\",\"email\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointments[0].guest.email").value(nullValue())));
    assertThat(row(id).get("guest_email")).isNull();
  }

  @Test
  void patientAppointmentHasPractitionerOriginAndNoGuest() throws Exception {
    String id =
        idOf(
            post("/api/appointments", base() + ",\"patientId\":\"" + theo + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointments[0].origin").value("practitioner"))
                .andExpect(jsonPath("$.appointments[0].guest").value(nullValue())));
    assertThat(row(id).get("origin")).isEqualTo("practitioner");
  }

  @Test
  void malformedEmailIs422InvalidFormatOnGuestEmail() throws Exception {
    post("/api/appointments", guestCreate("{\"lastName\":\"Benali\",\"email\":\"abc\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("guest.email"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    assertThat(appointmentsOfAlice()).isZero();
  }

  @Test
  void emailOfMoreThan320CharactersIs422OutOfRangeOnGuestEmail() throws Exception {
    String tooLong = "a".repeat(64) + "@" + "d".repeat(251) + ".test";
    assertThat(tooLong).hasSize(321);
    post(
            "/api/appointments",
            guestCreate("{\"lastName\":\"Benali\",\"email\":\"" + tooLong + "\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("guest.email"))
        .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));
    assertThat(appointmentsOfAlice()).isZero();
  }

  @Test
  void originInTheCreateBodyIs422UnknownField() throws Exception {
    post("/api/appointments", base() + ",\"origin\":\"online\",\"guest\":" + SARA + "}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("origin"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    assertThat(appointmentsOfAlice()).isZero();
  }

  @Test
  void guestSeriesCopiesTheEmailOnEveryOccurrence() throws Exception {
    post(
            "/api/appointments",
            base()
                + ",\"guest\":"
                + SARA
                + ",\"recurrence\":{\"everyWeeks\":1,\"count\":3},\"tz\":\"Europe/Paris\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appointments.length()").value(3))
        .andExpect(jsonPath("$.appointments[2].guest.email").value("sara@example.test"));
    assertThat(
            jdbc.queryForList(
                "select guest_email from appointment where practitioner_id = ?",
                String.class,
                alice))
        .hasSize(3)
        .containsOnly("sara@example.test");
  }

  // ---- modification

  @Test
  void patchWithEmptyEmailErasesItAndKeepsTheRest() throws Exception {
    String id = createGuest();
    patch(id, "{\"guest\":{\"lastName\":\"Benali\",\"email\":\"\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.guest.email").value(nullValue()))
        .andExpect(jsonPath("$.appointment.guest.firstName").value("Sara"));
    assertThat(row(id).get("guest_email")).isNull();
  }

  @Test
  void patchWithoutEmailKeepsIt() throws Exception {
    String id = createGuest();
    patch(id, "{\"guest\":{\"lastName\":\"Ben Ali\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.guest.lastName").value("Ben Ali"))
        .andExpect(jsonPath("$.appointment.guest.email").value("sara@example.test"));
    patch(id, "{\"guest\":{\"lastName\":\"Ben Ali\",\"email\":null}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.guest.email").value("sara@example.test"));
    assertThat(row(id).get("guest_email")).isEqualTo("sara@example.test");
  }

  @Test
  void patchReplacesTheEmail() throws Exception {
    String id = createGuest();
    patch(id, "{\"guest\":{\"lastName\":\"Benali\",\"email\":\"s.benali@example.test\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointment.guest.email").value("s.benali@example.test"));
    assertThat(row(id).get("guest_email")).isEqualTo("s.benali@example.test");
  }

  @Test
  void patchWithMalformedEmailIs422InvalidFormatAndChangesNothing() throws Exception {
    String id = createGuest();
    patch(id, "{\"guest\":{\"lastName\":\"Benali\",\"email\":\"sara@\"}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("guest.email"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    assertThat(row(id).get("guest_email")).isEqualTo("sara@example.test");
  }

  @Test
  void originInThePatchBodyIs422UnknownField() throws Exception {
    String id = createGuest();
    patch(id, "{\"origin\":\"online\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("origin"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));
    assertThat(row(id).get("origin")).isEqualTo("practitioner");
  }

  // ---- rattachement

  @Test
  void attachPatientClearsTheEmail() throws Exception {
    String id = createGuest();
    post("/api/appointments/" + id + "/attach-patient", "{\"patientId\":\"" + theo + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments[0].guest").value(nullValue()))
        .andExpect(jsonPath("$.appointments[0].origin").value("practitioner"));
    Map<String, Object> row = row(id);
    assertThat(row.get("patient_id")).isEqualTo(theo);
    assertThat(row.get("guest_email")).isNull();
    assertThat(row.get("guest_last_name")).isNull();
  }

  // ---- outils

  private static String base() {
    return "{\"startsAt\":\"" + SLOT + "\",\"durationMin\":50,\"modality\":\"in_person\"";
  }

  private static String guestCreate(String guest) {
    return base() + ",\"guest\":" + guest + "}";
  }

  private String createGuest() throws Exception {
    return idOf(post("/api/appointments", guestCreate(SARA)).andExpect(status().isCreated()));
  }

  private RequestPostProcessor me() {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            alice, tenant, alice + "@example.test", "Dr Agenda", "psychologist", "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions send(MockHttpServletRequestBuilder builder, String body) throws Exception {
    if (body != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.getBytes(StandardCharsets.UTF_8));
    }
    return mvc.perform(builder.with(me()));
  }

  private ResultActions post(String url, String body) throws Exception {
    return send(MockMvcRequestBuilders.post(url), body);
  }

  private ResultActions patch(String id, String body) throws Exception {
    return send(MockMvcRequestBuilders.patch("/api/appointments/" + id), body);
  }

  private ResultActions get(String url) throws Exception {
    return send(MockMvcRequestBuilders.get(url), null);
  }

  private static String idOf(ResultActions result) throws Exception {
    String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return JsonPath.read(body, "$.appointments[0].id");
  }

  private Map<String, Object> row(String id) {
    return jdbc.queryForMap(
        "select patient_id, origin, guest_last_name, guest_email from appointment where id = ?",
        UUID.fromString(id));
  }

  private int appointmentsOfAlice() {
    return jdbc.queryForObject(
        "select count(*) from appointment where practitioner_id = ?", Integer.class, alice);
  }
}
