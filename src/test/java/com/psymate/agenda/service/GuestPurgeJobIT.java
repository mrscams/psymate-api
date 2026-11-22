package com.psymate.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Purge des invités (M11-10, G Q60, E9) sur une vraie base : 6 mois après la fin, ou l'annulation,
 * d'un rendez-vous invité, nom, prénom, téléphone et e-mail passent à NULL et guest_purged_at est
 * posé ; la ligne reste. M14-04 (G Q82 c) : un rendez-vous en ligne annulé est purgé 30 jours après
 * son annulation. Les rendez-vous sont créés par l'API, puis vieillis (et passés à l'origine
 * online) en SQL : le test ne peut pas attendre six mois, et aucune route ne crée encore de
 * rendez-vous en ligne (M14-06).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class GuestPurgeJobIT {

  private static final String SARA =
      "{\"lastName\":\"Benali\",\"firstName\":\"Sara\",\"phone\":\"+33 6 12 34 56 78\","
          + "\"email\":\"sara@example.test\"}";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private GuestPurgeJob job;

  private UUID tenant;
  private UUID alice;
  private UUID theo;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet purge')", tenant);
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

  @Test
  void defaultRetentionIsSixMonths() {
    assertThat(job.retention()).isEqualTo(Duration.ofDays(180));
  }

  @Test
  void guestEndedSevenMonthsAgoIsPurgedRowKeptAndDtoShowsPurgedAt() throws Exception {
    String id = createGuest(daysAgo(213));
    int before = count();

    assertThat(job.run()).isGreaterThanOrEqualTo(1);

    Map<String, Object> row = row(id);
    assertThat(row.get("guest_first_name")).isNull();
    assertThat(row.get("guest_last_name")).isNull();
    assertThat(row.get("guest_phone")).isNull();
    assertThat(row.get("guest_email")).isNull();
    assertThat(row.get("guest_purged_at")).isNotNull();
    assertThat(row.get("patient_id")).isNull();
    assertThat(count()).isEqualTo(before);

    get("/api/appointments/" + id)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.patient").value(nullValue()))
        .andExpect(jsonPath("$.guest.purgedAt").isNotEmpty())
        .andExpect(jsonPath("$.guest.firstName").value(nullValue()))
        .andExpect(jsonPath("$.guest.lastName").value(nullValue()))
        .andExpect(jsonPath("$.guest.phone").value(nullValue()))
        .andExpect(jsonPath("$.guest.email").value(nullValue()));
  }

  @Test
  void guestEndedFiveMonthsAgoIsUntouched() throws Exception {
    String id = createGuest(daysAgo(152));
    job.run();
    Map<String, Object> row = row(id);
    assertThat(row.get("guest_last_name")).isEqualTo("Benali");
    assertThat(row.get("guest_phone")).isEqualTo("+33 6 12 34 56 78");
    assertThat(row.get("guest_purged_at")).isNull();
  }

  @Test
  void patientAppointmentEndedSevenMonthsAgoIsUntouched() throws Exception {
    String id =
        idOf(
            post("/api/appointments", body(daysAgo(213)) + ",\"patientId\":\"" + theo + "\"}")
                .andExpect(status().isCreated()));
    job.run();
    Map<String, Object> row = row(id);
    assertThat(row.get("patient_id")).isEqualTo(theo);
    assertThat(row.get("guest_purged_at")).isNull();
  }

  @Test
  void guestCancelledSevenMonthsAgoIsPurgedEvenIfItsSlotIsAhead() throws Exception {
    String id = createGuest(Instant.now().plus(30, ChronoUnit.DAYS));
    post("/api/appointments/" + id + "/cancel", "{}").andExpect(status().isOk());
    jdbc.update(
        "update appointment set cancelled_at = now() - interval '7 months' where id = ?",
        UUID.fromString(id));

    job.run();

    Map<String, Object> row = row(id);
    assertThat(row.get("guest_last_name")).isNull();
    assertThat(row.get("guest_purged_at")).isNotNull();
    assertThat(row.get("status")).isEqualTo("cancelled");
  }

  // ---- M14-04 : rendez-vous en ligne (G Q82 c et d)

  @Test
  void defaultOnlineCancelledRetentionIsThirtyDays() {
    assertThat(job.onlineCancelledRetention()).isEqualTo(Duration.ofDays(30));
  }

  @Test
  void onlineCancelledThirtyOneDaysAgoIsPurgedEmailIncluded() throws Exception {
    String id = cancelledDaysAgo(online(createGuest(inThirtyDays())), 31);

    job.run();

    Map<String, Object> row = row(id);
    assertThat(row.get("guest_first_name")).isNull();
    assertThat(row.get("guest_last_name")).isNull();
    assertThat(row.get("guest_phone")).isNull();
    assertThat(row.get("guest_email")).isNull();
    assertThat(row.get("guest_purged_at")).isNotNull();
    assertThat(row.get("status")).isEqualTo("cancelled");
    assertThat(row.get("origin")).isEqualTo("online");
  }

  @Test
  void onlineCancelledTwentyNineDaysAgoIsUntouched() throws Exception {
    String id = cancelledDaysAgo(online(createGuest(inThirtyDays())), 29);

    job.run();

    assertIntact(row(id));
  }

  @Test
  void onlineHeldFiveMonthsAgoIsUntouched() throws Exception {
    String id = online(createGuest(daysAgo(152)));

    job.run();

    assertIntact(row(id));
  }

  @Test
  void onlineHeldSevenMonthsAgoIsPurged() throws Exception {
    String id = online(createGuest(daysAgo(213)));

    job.run();

    Map<String, Object> row = row(id);
    assertThat(row.get("guest_last_name")).isNull();
    assertThat(row.get("guest_email")).isNull();
    assertThat(row.get("guest_purged_at")).isNotNull();
  }

  @Test
  void practitionerCancelledThirtyOneDaysAgoIsUntouched() throws Exception {
    String id = cancelledDaysAgo(createGuest(inThirtyDays()), 31);

    job.run();

    Map<String, Object> row = row(id);
    assertThat(row.get("origin")).isEqualTo("practitioner");
    assertThat(row.get("status")).isEqualTo("cancelled");
    assertIntact(row);
  }

  @Test
  void attachOrPatchGuestOnPurgedGuestIsRefused() throws Exception {
    String id = createGuest(daysAgo(213));
    job.run();

    post("/api/appointments/" + id + "/attach-patient", "{\"patientId\":\"" + theo + "\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_ACTIVE"));
    patch(id, "{\"guest\":{\"lastName\":\"Benali\"}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("guest"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    assertThat(row(id).get("patient_id")).isNull();
    assertThat(row(id).get("guest_last_name")).isNull();
  }

  @Test
  void conflictWithPurgedGuestShowsPurgedGuestLabel() throws Exception {
    // Un invité purgé par le job est passé ou annulé, donc jamais en conflit : le libellé se
    // vérifie sur un rendez-vous actif purgé en SQL (ce que ferait une rétention très courte).
    Instant start = Instant.now().plus(60, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    String id = createGuest(start);
    jdbc.update(
        "update appointment set guest_first_name = null, guest_last_name = null,"
            + " guest_phone = null, guest_purged_at = now() where id = ?",
        UUID.fromString(id));
    get("/api/agenda/freebusy?from=" + start + "&to=" + start.plus(30, ChronoUnit.MINUTES))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conflicts[0].appointmentId").value(id))
        .andExpect(jsonPath("$.conflicts[0].patientDisplayName").value("Invité purgé"));
  }

  // ---- outils

  private static Instant daysAgo(int days) {
    return Instant.now().minus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  }

  private static Instant inThirtyDays() {
    return Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  }

  /** Passe le rendez-vous à l'origine online (ce que fera M14-06) et renvoie son identifiant. */
  private String online(String id) {
    jdbc.update("update appointment set origin = 'online' where id = ?", UUID.fromString(id));
    return id;
  }

  /** Annule par l'API, puis recule l'annulation de {@code days} jours. */
  private String cancelledDaysAgo(String id, int days) throws Exception {
    post("/api/appointments/" + id + "/cancel", "{}").andExpect(status().isOk());
    jdbc.update(
        "update appointment set cancelled_at = now() - make_interval(days => ?) where id = ?",
        days,
        UUID.fromString(id));
    return id;
  }

  /** Invité non purgé : identité complète, e-mail compris. */
  private static void assertIntact(Map<String, Object> row) {
    assertThat(row.get("guest_first_name")).isEqualTo("Sara");
    assertThat(row.get("guest_last_name")).isEqualTo("Benali");
    assertThat(row.get("guest_phone")).isEqualTo("+33 6 12 34 56 78");
    assertThat(row.get("guest_email")).isEqualTo("sara@example.test");
    assertThat(row.get("guest_purged_at")).isNull();
  }

  private static String body(Instant startsAt) {
    return "{\"startsAt\":\"" + startsAt + "\",\"durationMin\":50,\"modality\":\"in_person\"";
  }

  private String createGuest(Instant startsAt) throws Exception {
    return idOf(
        post("/api/appointments", body(startsAt) + ",\"guest\":" + SARA + "}")
            .andExpect(status().isCreated()));
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
        "select patient_id, status, origin, guest_first_name, guest_last_name, guest_phone,"
            + " guest_email, guest_purged_at from appointment where id = ?",
        UUID.fromString(id));
  }

  private int count() {
    return jdbc.queryForObject(
        "select count(*) from appointment where practitioner_id = ?", Integer.class, alice);
  }
}
