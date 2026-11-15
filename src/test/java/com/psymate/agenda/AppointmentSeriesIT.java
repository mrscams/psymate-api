package com.psymate.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.agenda.api.AppointmentCancelled;
import com.psymate.agenda.api.AppointmentPlanned;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Séries de rendez-vous (M11-07, docs/I-plan-agenda-api.md I5, G Q55) sur une vraie base
 * (Testcontainers pgvector/pgvector:pg16) : un cas par critère d'acceptation, deux praticiens du
 * même cabinet (Alice, propriétaire des patients, et Bob).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, AppointmentSeriesIT.ListenerConfig.class})
class AppointmentSeriesIT {

  /**
   * Abonnés de test : le registre Modulith n'écrit une publication dans {@code event_publication}
   * que s'il existe au moins un abonné ; l'agenda n'en a aucun en V1 (G Q59).
   */
  static class SeriesEventsListener {
    @ApplicationModuleListener
    void on(AppointmentPlanned event) {}

    @ApplicationModuleListener
    void on(AppointmentCancelled event) {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ListenerConfig {
    @Bean
    SeriesEventsListener seriesEventsListener() {
      return new SeriesEventsListener();
    }
  }

  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

  /** Lundi 16 novembre 2026, 10:00 à Paris. */
  private static final String MONDAY_TEN = "2026-11-16T09:00:00Z";

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
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet séries')", tenant);
    alice = practitioner();
    bob = practitioner();
    theo = patient(alice, "Théo B.");
    bobsPatient = patient(bob, "Patient de Bob");
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- création d'une série

  @Test
  void everyWeekCountFourCreatesFourRowsOfOneSeriesWithFourPlannedEventsAndLocationOfTheFirst()
      throws Exception {
    ResultActions result =
        post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":4"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.appointments.length()").value(4))
            .andExpect(jsonPath("$.conflicts.length()").value(0))
            .andExpect(jsonPath("$.appointments[0].startsAt").value(MONDAY_TEN))
            .andExpect(jsonPath("$.appointments[1].startsAt").value("2026-11-23T09:00:00Z"))
            .andExpect(jsonPath("$.appointments[2].startsAt").value("2026-11-30T09:00:00Z"))
            .andExpect(jsonPath("$.appointments[3].startsAt").value("2026-12-07T09:00:00Z"))
            .andExpect(jsonPath("$.appointments[3].endsAt").value("2026-12-07T09:50:00Z"));
    String first = JsonPath.read(body(result), "$.appointments[0].id");
    String seriesId = JsonPath.read(body(result), "$.appointments[0].seriesId");
    result.andExpect(header().string("Location", "/api/appointments/" + first));
    List<String> seriesIds = JsonPath.read(body(result), "$.appointments[*].seriesId");
    assertThat(seriesIds).hasSize(4).containsOnly(seriesId);

    assertThat(seriesRows(seriesId)).isEqualTo(4);
    assertThat(appointmentsOf(alice)).isEqualTo(4);
    assertThat(publications(AppointmentPlanned.class, seriesId)).isEqualTo(4);
  }

  @Test
  void everyTwoWeeksUntilStartPlusSevenWeeksCreatesWeeksZeroTwoFourSix() throws Exception {
    post(
            alice,
            "/api/appointments",
            series(theo, MONDAY_TEN, "\"everyWeeks\":2,\"until\":\"2027-01-04\""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appointments.length()").value(4))
        .andExpect(jsonPath("$.appointments[0].startsAt").value(MONDAY_TEN))
        .andExpect(jsonPath("$.appointments[1].startsAt").value("2026-11-30T09:00:00Z"))
        .andExpect(jsonPath("$.appointments[2].startsAt").value("2026-12-14T09:00:00Z"))
        .andExpect(jsonPath("$.appointments[3].startsAt").value("2026-12-28T09:00:00Z"));
  }

  @Test
  void allOccurrencesShareStatusAndPlanningNote() throws Exception {
    String body =
        withField(
            withField(
                series(theo, MONDAY_TEN, "\"everyWeeks\":4,\"count\":3"), "\"tentative\":true"),
            "\"planningNote\":\"Apporter le carnet\"");
    ResultActions result = post(alice, "/api/appointments", body).andExpect(status().isCreated());
    String seriesId = JsonPath.read(body(result), "$.appointments[0].seriesId");
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select status, planning_note from appointment where series_id = ?",
            UUID.fromString(seriesId));
    assertThat(rows).hasSize(3);
    assertThat(rows)
        .allSatisfy(
            r -> {
              assertThat(r.get("status")).isEqualTo("tentative");
              assertThat(r.get("planning_note")).isEqualTo("Apporter le carnet");
            });
  }

  @Test
  void exactlyTwentySixOccurrencesAreCreated() throws Exception {
    post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":26"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appointments.length()").value(26));
    assertThat(appointmentsOf(alice)).isEqualTo(26);
  }

  @Test
  void countTwentySevenIs422TooManyOccurrencesAndNothingIsCreated() throws Exception {
    post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":27"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("TOO_MANY_OCCURRENCES"))
        .andExpect(jsonPath("$.errors[0].field").value("recurrence"))
        .andExpect(jsonPath("$.errors[0].code").value("TOO_MANY_OCCURRENCES"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    assertThat(appointmentsOf(alice)).isZero();
  }

  @Test
  void untilTooFarEveryWeekIs422TooManyOccurrencesAndNothingIsCreated() throws Exception {
    // 16 novembre 2026 + 26 semaines = 17 mai 2027 : 27 occurrences.
    post(
            alice,
            "/api/appointments",
            series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"until\":\"2027-05-17\""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("TOO_MANY_OCCURRENCES"))
        .andExpect(jsonPath("$.errors[0].field").value("recurrence"));
    assertThat(appointmentsOf(alice)).isZero();
  }

  @Test
  void countAndUntilTogetherIs422InvalidValueOnRecurrence() throws Exception {
    post(
            alice,
            "/api/appointments",
            series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":4,\"until\":\"2026-12-28\""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("recurrence"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    assertThat(appointmentsOf(alice)).isZero();
  }

  @Test
  void neitherCountNorUntilIs422RequiredOnRecurrence() throws Exception {
    post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":1"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("recurrence"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    assertThat(appointmentsOf(alice)).isZero();
  }

  @Test
  void everyThreeWeeksIs422InvalidValue() throws Exception {
    post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":3,\"count\":4"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("recurrence.everyWeeks"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    assertThat(appointmentsOf(alice)).isZero();
  }

  @Test
  void missingEveryWeeksZeroCountUntilBeforeStartAndUnknownZoneAre422() throws Exception {
    post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"count\":4"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("recurrence.everyWeeks"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":0"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("recurrence.count"))
        .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));
    post(
            alice,
            "/api/appointments",
            series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"until\":\"2026-11-15\""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("recurrence.until"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    post(
            alice,
            "/api/appointments",
            withField(
                series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":2"),
                "\"tz\":\"Mars/Olympus\""))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("tz"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    assertThat(appointmentsOf(alice)).isZero();
  }

  @Test
  void seriesOnAPatientOfAnotherPractitionerIs403AndCreatesNothing() throws Exception {
    post(
            alice,
            "/api/appointments",
            series(bobsPatient, MONDAY_TEN, "\"everyWeeks\":1,\"count\":4"))
        .andExpect(status().isForbidden());
    assertThat(appointmentsOf(alice)).isZero();
  }

  // ---- conflits

  @Test
  void anOccurrenceOverlappingAnExistingAppointmentIs201WithOnlyThatOccurrenceInConflicts()
      throws Exception {
    // Rendez-vous existant le lundi 30 novembre 09:30Z–10:20Z : chevauche la troisième occurrence.
    String existing = createSingle(alice, theo, "2026-11-30T09:30:00Z");
    post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":4"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appointments.length()").value(4))
        .andExpect(jsonPath("$.conflicts.length()").value(1))
        .andExpect(jsonPath("$.conflicts[0].appointmentId").value(existing))
        .andExpect(jsonPath("$.conflicts[0].occurrenceStartsAt").value("2026-11-30T09:00:00Z"))
        .andExpect(jsonPath("$.conflicts[0].startsAt").value("2026-11-30T09:30:00Z"))
        .andExpect(jsonPath("$.conflicts[0].patientDisplayName").value("Théo B."));
    assertThat(appointmentsOf(alice)).isEqualTo(5);
  }

  @Test
  void aSingleAppointmentConflictCarriesItsOwnStartAsOccurrenceStartsAt() throws Exception {
    String existing = createSingle(alice, theo, MONDAY_TEN);
    post(alice, "/api/appointments", single(theo, "2026-11-16T09:30:00Z"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appointments[0].seriesId").value(nullValue()))
        .andExpect(jsonPath("$.conflicts[0].appointmentId").value(existing))
        .andExpect(jsonPath("$.conflicts[0].occurrenceStartsAt").value("2026-11-16T09:30:00Z"));
  }

  // ---- changement d'heure

  @Test
  void aSeriesAcrossTheLastSundayOfOctoberKeepsTheSameLocalTime() throws Exception {
    // Lundi 12 octobre 2026, 10:00 à Paris (UTC+2) ; heure d'hiver le dimanche 25 octobre.
    ResultActions result =
        post(
                alice,
                "/api/appointments",
                withField(
                    series(theo, "2026-10-12T08:00:00Z", "\"everyWeeks\":1,\"count\":4"),
                    "\"tz\":\"Europe/Paris\""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.appointments[0].startsAt").value("2026-10-12T08:00:00Z"))
            .andExpect(jsonPath("$.appointments[1].startsAt").value("2026-10-19T08:00:00Z"))
            .andExpect(jsonPath("$.appointments[2].startsAt").value("2026-10-26T09:00:00Z"))
            .andExpect(jsonPath("$.appointments[3].startsAt").value("2026-11-02T09:00:00Z"));
    List<String> starts = JsonPath.read(body(result), "$.appointments[*].startsAt");
    assertThat(starts)
        .allSatisfy(
            s ->
                assertThat(Instant.parse(s).atZone(PARIS).toLocalTime())
                    .isEqualTo(LocalTime.of(10, 0)));
  }

  @Test
  void withoutTzTheSeriesIsComputedInEuropeParis() throws Exception {
    post(
            alice,
            "/api/appointments",
            series(theo, "2026-10-19T08:00:00Z", "\"everyWeeks\":1,\"count\":2"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appointments[1].startsAt").value("2026-10-26T09:00:00Z"));
  }

  // ---- annulation de série

  @Test
  void cancelSeriesCancelsActiveOccurrencesIncludingAMovedOneAndLeavesTheCancelledOneAsIs()
      throws Exception {
    ResultActions created =
        post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":4"))
            .andExpect(status().isCreated());
    String seriesId = JsonPath.read(body(created), "$.appointments[0].seriesId");
    List<String> ids = JsonPath.read(body(created), "$.appointments[*].id");
    // Une occurrence déjà annulée seule, une autre déplacée seule.
    post(alice, "/api/appointments/" + ids.get(1) + "/cancel", "{\"reason\":\"Malade\"}")
        .andExpect(status().isOk());
    patch(alice, ids.get(2), "{\"startsAt\":\"2026-12-01T14:00:00Z\"}").andExpect(status().isOk());
    int before = appointmentsOf(alice);

    post(alice, "/api/appointments/series/" + seriesId + "/cancel", "{\"reason\":\"Fin du suivi\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments.length()").value(4))
        .andExpect(jsonPath("$.appointments[0].status").value("cancelled"))
        .andExpect(jsonPath("$.appointments[0].cancelReason").value("Fin du suivi"))
        .andExpect(jsonPath("$.appointments[1].id").value(ids.get(1)))
        .andExpect(jsonPath("$.appointments[1].cancelReason").value("Malade"))
        .andExpect(jsonPath("$.appointments[2].id").value(ids.get(2)))
        .andExpect(jsonPath("$.appointments[2].startsAt").value("2026-12-01T14:00:00Z"))
        .andExpect(jsonPath("$.appointments[2].status").value("cancelled"))
        .andExpect(jsonPath("$.appointments[2].seriesId").value(seriesId))
        .andExpect(jsonPath("$.appointments[3].status").value("cancelled"));

    // Trois occurrences annulées par la série (dont la déplacée), une l'était déjà : quatre en
    // tout.
    assertThat(publications(AppointmentCancelled.class, seriesId)).isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment where series_id = ? and status = 'cancelled'"
                    + " and cancel_reason = 'Fin du suivi'",
                Integer.class,
                UUID.fromString(seriesId)))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "select series_id from appointment where id = ?",
                UUID.class,
                UUID.fromString(ids.get(2))))
        .isEqualTo(UUID.fromString(seriesId));
    assertThat(appointmentsOf(alice)).isEqualTo(before);
  }

  @Test
  void cancelSeriesLeavesDoneOccurrencesUntouchedAndIsIdempotent() throws Exception {
    ResultActions created =
        post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":3"))
            .andExpect(status().isCreated());
    String seriesId = JsonPath.read(body(created), "$.appointments[0].seriesId");
    List<String> ids = JsonPath.read(body(created), "$.appointments[*].id");
    jdbc.update("update appointment set status = 'done' where id = ?", UUID.fromString(ids.get(0)));

    post(alice, "/api/appointments/series/" + seriesId + "/cancel", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments[0].status").value("done"))
        .andExpect(jsonPath("$.appointments[1].status").value("cancelled"))
        .andExpect(jsonPath("$.appointments[2].status").value("cancelled"));
    assertThat(publications(AppointmentCancelled.class, seriesId)).isEqualTo(2);

    post(alice, "/api/appointments/series/" + seriesId + "/cancel", "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments.length()").value(3));
    assertThat(publications(AppointmentCancelled.class, seriesId)).isEqualTo(2);
  }

  @Test
  void cancelSeriesOfAnotherPractitionerOrUnknownIs404SeriesNotFound() throws Exception {
    ResultActions created =
        post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":2"))
            .andExpect(status().isCreated());
    String seriesId = JsonPath.read(body(created), "$.appointments[0].seriesId");

    post(bob, "/api/appointments/series/" + seriesId + "/cancel", "{}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SERIES_NOT_FOUND"));
    post(alice, "/api/appointments/series/" + UUID.randomUUID() + "/cancel", "{}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SERIES_NOT_FOUND"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment where series_id = ? and status = 'planned'",
                Integer.class,
                UUID.fromString(seriesId)))
        .isEqualTo(2);
  }

  // ---- aucune propagation, aucune suppression

  @Test
  void patchAndCancelOfOneOccurrenceDoNotPropagateToTheSeries() throws Exception {
    ResultActions created =
        post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":3"))
            .andExpect(status().isCreated());
    List<String> ids = JsonPath.read(body(created), "$.appointments[*].id");

    patch(alice, ids.get(0), "{\"startsAt\":\"2026-11-17T09:00:00Z\",\"durationMin\":30}")
        .andExpect(status().isOk());
    post(alice, "/api/appointments/" + ids.get(1) + "/cancel", "{}").andExpect(status().isOk());

    get(alice, "/api/appointments/" + ids.get(2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("planned"))
        .andExpect(jsonPath("$.startsAt").value("2026-11-30T09:00:00Z"))
        .andExpect(jsonPath("$.durationMin").value(50));
    get(alice, "/api/appointments/" + ids.get(1))
        .andExpect(jsonPath("$.startsAt").value("2026-11-23T09:00:00Z"))
        .andExpect(jsonPath("$.durationMin").value(50));
  }

  @Test
  void noAppointmentRowIsEverDeletedBySeriesOperations() throws Exception {
    ResultActions created =
        post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":2,\"count\":5"))
            .andExpect(status().isCreated());
    String seriesId = JsonPath.read(body(created), "$.appointments[0].seriesId");
    int before = appointmentsOf(alice);
    assertThat(before).isEqualTo(5);
    post(alice, "/api/appointments/series/" + seriesId + "/cancel", "{}")
        .andExpect(status().isOk());
    post(alice, "/api/appointments/series/" + seriesId + "/cancel", "{}")
        .andExpect(status().isOk());
    assertThat(appointmentsOf(alice)).isEqualTo(before);
    assertThat(seriesRows(seriesId)).isEqualTo(5);
  }

  // ---- lecture de l'agenda

  @Test
  void agendaWindowReturnsSeriesIdOnEveryOccurrence() throws Exception {
    ResultActions created =
        post(alice, "/api/appointments", series(theo, MONDAY_TEN, "\"everyWeeks\":1,\"count\":3"))
            .andExpect(status().isCreated());
    String seriesId = JsonPath.read(body(created), "$.appointments[0].seriesId");
    createSingle(alice, theo, "2026-11-18T09:00:00Z");

    ResultActions window =
        get(alice, "/api/agenda?from=2026-11-16T00:00:00Z&to=2026-12-07T00:00:00Z")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.appointments.length()").value(4));
    List<String> seriesIds = JsonPath.read(body(window), "$.appointments[*].seriesId");
    assertThat(seriesIds).hasSize(4);
    assertThat(seriesIds.stream().filter(Objects::nonNull).toList())
        .hasSize(3)
        .containsOnly(seriesId);
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

  private static String single(UUID patientId, String startsAt) {
    return "{\"patientId\":\""
        + patientId
        + "\",\"startsAt\":\""
        + startsAt
        + "\",\"durationMin\":50,\"modality\":\"in_person\"}";
  }

  /** Création d'une série de 50 minutes au cabinet ; {@code recurrence} = contenu de l'objet. */
  private static String series(UUID patientId, String startsAt, String recurrence) {
    return withField(single(patientId, startsAt), "\"recurrence\":{" + recurrence + "}");
  }

  /** Ajoute une paire clé-valeur JSON à la fin d'un objet. */
  private static String withField(String json, String pair) {
    return json.substring(0, json.length() - 1) + "," + pair + "}";
  }

  private String createSingle(UUID who, UUID patientId, String startsAt) throws Exception {
    return JsonPath.read(
        body(
            post(who, "/api/appointments", single(patientId, startsAt))
                .andExpect(status().isCreated())),
        "$.appointments[0].id");
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private int appointmentsOf(UUID practitionerId) {
    return jdbc.queryForObject(
        "select count(*) from appointment where practitioner_id = ?",
        Integer.class,
        practitionerId);
  }

  private int seriesRows(String seriesId) {
    return jdbc.queryForObject(
        "select count(*) from appointment where series_id = ?",
        Integer.class,
        UUID.fromString(seriesId));
  }

  /** Publications du registre pour notre abonné, pour un type d'événement et une série. */
  private int publications(Class<?> eventType, String seriesId) {
    return jdbc.queryForObject(
        "select count(*) from event_publication where listener_id like '%AppointmentSeriesIT%'"
            + " and event_type = ? and serialized_event::text like ?",
        Integer.class, eventType.getName(), "%" + seriesId + "%");
  }
}
