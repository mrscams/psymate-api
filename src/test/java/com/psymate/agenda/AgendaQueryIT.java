package com.psymate.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Lectures de l'agenda (M11-06, docs/I-plan-agenda-api.md I3 et I5) sur une vraie base
 * (Testcontainers pgvector/pgvector:pg16) : fenêtre {@code GET /api/agenda}, voisinage {@code GET
 * /api/agenda/freebusy}, rendez-vous d'un patient {@code GET /api/patients/{id}/appointments}. Deux
 * praticiens du même cabinet ; les rendez-vous sont posés en SQL (M11-05, qui crée l'API
 * d'écriture, n'est pas encore fusionnée). Les dates sont en 2030 pour rester dans le futur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class AgendaQueryIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;
  private UUID alice;
  private UUID bob;
  private UUID theo;
  private UUID lea;
  private UUID bobsPatient;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet agenda')", tenant);
    alice = practitioner();
    bob = practitioner();
    theo = patient(alice, "Théo B.", "{}");
    lea = patient(alice, "Léa M.", "{}");
    bobsPatient = patient(bob, "Patient de Bob", "{}");
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- données

  private UUID practitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'T', 'P', 'Dr Agenda', 'psychologist', 'approved')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  private UUID patient(UUID owner, String name, String followUp) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name, birth_date, follow_up)"
            + " values (?, ?, ?, ?, ?, ?::jsonb)",
        id,
        tenant,
        owner,
        name,
        LocalDate.of(1990, 4, 2),
        followUp);
    return id;
  }

  private UUID appointment(UUID practitioner, UUID patient, String startsAt, int minutes) {
    return appointment(practitioner, patient, startsAt, minutes, "planned");
  }

  /** Rendez-vous inséré en SQL ; un annulé reçoit cancelled_at (ck_appointment_cancel). */
  private UUID appointment(
      UUID practitioner, UUID patient, String startsAt, int minutes, String status) {
    UUID id = UUID.randomUUID();
    Instant start = Instant.parse(startsAt);
    jdbc.update(
        "insert into appointment (id, tenant_id, practitioner_id, patient_id, starts_at, ends_at,"
            + " modality, status, cancelled_at) values (?, ?, ?, ?, ?, ?, 'in_person', ?, ?)",
        id,
        tenant,
        practitioner,
        patient,
        Timestamp.from(start),
        Timestamp.from(start.plusSeconds(minutes * 60L)),
        status,
        "cancelled".equals(status) ? Timestamp.from(Instant.parse("2029-12-01T00:00:00Z")) : null);
    return id;
  }

  private long appointmentCount() {
    return jdbc.queryForObject(
        "select count(*) from appointment where tenant_id = ?", Long.class, tenant);
  }

  // ---- requêtes

  private RequestPostProcessor as(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            tenant,
            practitionerId + "@example.test",
            "Dr Agenda",
            "psychologist",
            "approved");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions call(UUID practitioner, String url) throws Exception {
    return mvc.perform(get(url).with(as(practitioner)));
  }

  private static List<String> read(ResultActions result, String path) throws Exception {
    return JsonPath.read(
        result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), path);
  }

  private static final String WEEK = "from=2030-03-11T00:00:00Z&to=2030-03-18T00:00:00Z";

  // ---- GET /api/agenda

  @Test
  void weekWithOneCancelledReturnsTwoThenThreeWithIncludeCancelled() throws Exception {
    UUID first = appointment(alice, theo, "2030-03-12T08:00:00Z", 50);
    UUID second = appointment(alice, lea, "2030-03-11T09:00:00Z", 50);
    UUID cancelled = appointment(alice, theo, "2030-03-13T10:00:00Z", 50, "cancelled");
    appointment(alice, theo, "2030-03-18T00:00:00Z", 50); // commence à to : exclu

    ResultActions active =
        call(alice, "/api/agenda?" + WEEK)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.from").value("2030-03-11T00:00:00Z"))
            .andExpect(jsonPath("$.to").value("2030-03-18T00:00:00Z"))
            .andExpect(jsonPath("$.appointments", hasSize(2)))
            .andExpect(jsonPath("$.busy", hasSize(0)))
            .andExpect(jsonPath("$.appointments[0].startsAt").value("2030-03-11T09:00:00Z"))
            .andExpect(jsonPath("$.appointments[0].endsAt").value("2030-03-11T09:50:00Z"))
            .andExpect(jsonPath("$.appointments[0].durationMin").value(50))
            .andExpect(jsonPath("$.appointments[0].modality").value("in_person"))
            .andExpect(jsonPath("$.appointments[0].status").value("planned"))
            .andExpect(jsonPath("$.appointments[0].patient.id").value(lea.toString()))
            .andExpect(jsonPath("$.appointments[0].patient.displayName").value("Léa M."))
            .andExpect(jsonPath("$.appointments[0].patient.birthDate").value("1990-04-02"))
            .andExpect(jsonPath("$.appointments[0].patient.archived").value(false))
            .andExpect(jsonPath("$.appointments[0].seriesId").value(nullValue()));
    assertThat(read(active, "$.appointments[*].id"))
        .containsExactly(second.toString(), first.toString());

    ResultActions all =
        call(alice, "/api/agenda?" + WEEK + "&includeCancelled=true")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.appointments", hasSize(3)))
            .andExpect(jsonPath("$.appointments[2].status").value("cancelled"))
            .andExpect(jsonPath("$.appointments[2].cancelledAt").value("2029-12-01T00:00:00Z"));
    assertThat(read(all, "$.appointments[*].id"))
        .containsExactly(second.toString(), first.toString(), cancelled.toString());
  }

  @Test
  void appointmentStraddlingFromIsIncludedAndOneEndingAtFromIsNot() throws Exception {
    UUID straddling = appointment(alice, theo, "2030-03-10T23:30:00Z", 60);
    appointment(alice, theo, "2030-03-10T23:00:00Z", 60); // finit à from : exclu

    ResultActions result = call(alice, "/api/agenda?" + WEEK).andExpect(status().isOk());

    assertThat(read(result, "$.appointments[*].id")).containsExactly(straddling.toString());
  }

  @Test
  void appointmentsOfAnotherPractitionerNeverAppear() throws Exception {
    UUID mine = appointment(alice, theo, "2030-03-12T08:00:00Z", 50);
    UUID bobs = appointment(bob, bobsPatient, "2030-03-12T08:00:00Z", 50);

    ResultActions forAlice =
        call(alice, "/api/agenda?" + WEEK + "&includeCancelled=true").andExpect(status().isOk());
    ResultActions forBob = call(bob, "/api/agenda?" + WEEK).andExpect(status().isOk());

    assertThat(read(forAlice, "$.appointments[*].id")).containsExactly(mine.toString());
    assertThat(read(forBob, "$.appointments[*].id")).containsExactly(bobs.toString());
  }

  @Test
  void windowOfSixtyThreeDaysIsTooLargeAndSixtyTwoIsAccepted() throws Exception {
    call(alice, "/api/agenda?from=2030-03-01T00:00:00Z&to=2030-05-03T00:00:00Z")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("WINDOW_TOO_LARGE"))
        .andExpect(jsonPath("$.errors[0].field").value("to"))
        .andExpect(jsonPath("$.errors[0].code").value("WINDOW_TOO_LARGE"));

    call(alice, "/api/agenda?from=2030-03-01T00:00:00Z&to=2030-05-02T00:00:00Z")
        .andExpect(status().isOk());
  }

  @Test
  void fromNotBeforeToIsRejected() throws Exception {
    call(alice, "/api/agenda?from=2030-03-11T00:00:00Z&to=2030-03-11T00:00:00Z")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("to"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

    call(alice, "/api/agenda?from=2030-03-12T00:00:00Z&to=2030-03-11T00:00:00Z")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
  }

  @Test
  void missingFromIsRequiredAndUnreadableDateIsInvalidFormat() throws Exception {
    call(alice, "/api/agenda?to=2030-03-11T00:00:00Z")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].field").value("from"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

    call(alice, "/api/agenda?from=lundi&to=2030-03-11T00:00:00Z")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("from"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
  }

  // ---- GET /api/agenda/freebusy (12 mars 2030, Europe/Paris = UTC+1)

  @Test
  void freeSlotBetweenTwoAppointmentsHasNoConflictAndBothNeighbours() throws Exception {
    UUID morning = appointment(alice, theo, "2030-03-12T08:00:00Z", 50); // 09:00–09:50 Paris
    UUID noon = appointment(alice, lea, "2030-03-12T10:30:00Z", 50); // 11:30–12:20 Paris
    appointment(alice, lea, "2030-03-12T09:00:00Z", 30, "cancelled"); // ne compte jamais
    appointment(alice, theo, "2030-03-11T19:00:00Z", 50); // veille : jamais « précédent »
    appointment(alice, theo, "2030-03-13T08:00:00Z", 50); // lendemain : jamais « suivant »
    appointment(bob, bobsPatient, "2030-03-12T09:00:00Z", 50); // autre praticien

    call(alice, "/api/agenda/freebusy?from=2030-03-12T09:00:00Z&to=2030-03-12T09:50:00Z")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conflicts", hasSize(0)))
        .andExpect(jsonPath("$.previous.id").value(morning.toString()))
        .andExpect(jsonPath("$.previous.patient.displayName").value("Théo B."))
        .andExpect(jsonPath("$.next.id").value(noon.toString()))
        .andExpect(jsonPath("$.next.startsAt").value("2030-03-12T10:30:00Z"))
        .andExpect(jsonPath("$.next.patient.displayName").value("Léa M."));
  }

  @Test
  void overlappingSlotReportsTheConflict() throws Exception {
    UUID morning = appointment(alice, theo, "2030-03-12T08:00:00Z", 50);
    appointment(alice, lea, "2030-03-12T10:30:00Z", 50);

    call(alice, "/api/agenda/freebusy?from=2030-03-12T08:30:00Z&to=2030-03-12T09:20:00Z")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conflicts", hasSize(1)))
        .andExpect(jsonPath("$.conflicts[0].appointmentId").value(morning.toString()))
        .andExpect(jsonPath("$.conflicts[0].startsAt").value("2030-03-12T08:00:00Z"))
        .andExpect(jsonPath("$.conflicts[0].endsAt").value("2030-03-12T08:50:00Z"))
        .andExpect(jsonPath("$.conflicts[0].patientDisplayName").value("Théo B."))
        .andExpect(jsonPath("$.previous").value(nullValue()));
  }

  @Test
  void excludeAppointmentIdRemovesTheMovedAppointmentFromItsOwnConflicts() throws Exception {
    UUID moved = appointment(alice, theo, "2030-03-12T08:00:00Z", 50);
    String slot = "from=2030-03-12T08:30:00Z&to=2030-03-12T09:20:00Z";

    call(alice, "/api/agenda/freebusy?" + slot)
        .andExpect(jsonPath("$.conflicts[0].appointmentId").value(moved.toString()));
    call(alice, "/api/agenda/freebusy?" + slot + "&excludeAppointmentId=" + moved)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conflicts", hasSize(0)))
        .andExpect(jsonPath("$.previous").value(nullValue()))
        .andExpect(jsonPath("$.next").value(nullValue()));
  }

  @Test
  void adjacentAppointmentsDoNotConflict() throws Exception {
    UUID before = appointment(alice, theo, "2030-03-12T08:00:00Z", 60); // finit à 09:00Z
    UUID after = appointment(alice, lea, "2030-03-12T10:00:00Z", 60); // commence à 10:00Z

    call(alice, "/api/agenda/freebusy?from=2030-03-12T09:00:00Z&to=2030-03-12T10:00:00Z")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conflicts", hasSize(0)))
        .andExpect(jsonPath("$.previous.id").value(before.toString()))
        .andExpect(jsonPath("$.next.id").value(after.toString()));
  }

  @Test
  void tzDecidesTheCalendarDayOfTheNeighbours() throws Exception {
    // 22:00Z le 12 = 23:00 à Paris le 12 ; 23:30Z le 12 = 00:30 à Paris le 13.
    UUID late = appointment(alice, theo, "2030-03-12T22:00:00Z", 50);
    String slot = "from=2030-03-12T23:30:00Z&to=2030-03-13T00:00:00Z";

    call(alice, "/api/agenda/freebusy?" + slot)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previous").value(nullValue()));
    call(alice, "/api/agenda/freebusy?" + slot + "&tz=UTC")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previous.id").value(late.toString()));
    call(alice, "/api/agenda/freebusy?" + slot + "&tz=Mars/Olympus")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("tz"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
  }

  @Test
  void freebusyValidatesItsBounds() throws Exception {
    call(alice, "/api/agenda/freebusy?to=2030-03-12T09:00:00Z")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("from"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    call(alice, "/api/agenda/freebusy?from=2030-03-12T09:00:00Z&to=2030-03-12T09:00:00Z")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
  }

  @Test
  void freebusyNeverSeesAnotherPractitionersAppointments() throws Exception {
    appointment(bob, bobsPatient, "2030-03-12T08:00:00Z", 50);

    call(alice, "/api/agenda/freebusy?from=2030-03-12T08:00:00Z&to=2030-03-12T08:50:00Z")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conflicts", hasSize(0)))
        .andExpect(jsonPath("$.previous").value(nullValue()))
        .andExpect(jsonPath("$.next").value(nullValue()));
  }

  // ---- GET /api/patients/{id}/appointments

  @Test
  void patientOfAnotherPractitionerIsForbidden() throws Exception {
    call(alice, "/api/patients/" + bobsPatient + "/appointments")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
  }

  @Test
  void defaultsComeFromTheFollowUp() throws Exception {
    UUID followed =
        patient(
            alice,
            "Suivi complet",
            "{\"durationMin\": 50, \"modality\": \"video\", \"rhythm\": \"toutes les 2 semaines\"}");

    call(alice, "/api/patients/" + followed + "/appointments")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appointments", hasSize(0)))
        .andExpect(jsonPath("$.defaults.durationMin").value(50))
        .andExpect(jsonPath("$.defaults.modality").value("video"))
        .andExpect(jsonPath("$.defaults.rhythm").value("toutes les 2 semaines"));
  }

  @Test
  void emptyFollowUpFallsBackToThePreferenceAndInPerson() throws Exception {
    jdbc.update(
        "update practitioner set preferences = '{\"sessions.defaultDurationMin\": 45}'::jsonb"
            + " where id = ?",
        alice);

    call(alice, "/api/patients/" + theo + "/appointments")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaults.durationMin").value(45))
        .andExpect(jsonPath("$.defaults.modality").value("in_person"))
        .andExpect(jsonPath("$.defaults.rhythm").value(nullValue()));
  }

  @Test
  void withoutFollowUpNorPreferenceTheDefaultIsSixtyAndAnUnknownModalityIsInPerson()
      throws Exception {
    UUID email = patient(alice, "Modalité libre", "{\"modality\": \"email\"}");

    call(alice, "/api/patients/" + email + "/appointments")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaults.durationMin").value(60))
        .andExpect(jsonPath("$.defaults.modality").value("in_person"));
  }

  @Test
  void patientAppointmentsAreActiveNotFinishedSortedAndLimited() throws Exception {
    appointment(alice, theo, "2020-01-06T08:00:00Z", 50); // passé
    UUID third = appointment(alice, theo, "2030-03-20T08:00:00Z", 50);
    UUID first = appointment(alice, theo, "2030-03-06T08:00:00Z", 50);
    UUID second = appointment(alice, theo, "2030-03-13T08:00:00Z", 50, "tentative");
    appointment(alice, theo, "2030-03-10T08:00:00Z", 50, "cancelled");
    appointment(alice, theo, "2030-03-11T08:00:00Z", 50, "done");
    appointment(alice, lea, "2030-03-12T08:00:00Z", 50); // autre patient

    ResultActions all =
        call(alice, "/api/patients/" + theo + "/appointments").andExpect(status().isOk());
    assertThat(read(all, "$.appointments[*].id"))
        .containsExactly(first.toString(), second.toString(), third.toString());

    ResultActions limited =
        call(alice, "/api/patients/" + theo + "/appointments?limit=2").andExpect(status().isOk());
    assertThat(read(limited, "$.appointments[*].id"))
        .containsExactly(first.toString(), second.toString());

    // from au milieu du premier : il n'est pas terminé, il reste.
    ResultActions fromMid =
        call(alice, "/api/patients/" + theo + "/appointments?from=2030-03-06T08:30:00Z")
            .andExpect(status().isOk());
    assertThat(read(fromMid, "$.appointments[*].id"))
        .containsExactly(first.toString(), second.toString(), third.toString());

    ResultActions fromAfter =
        call(alice, "/api/patients/" + theo + "/appointments?from=2030-03-06T08:50:00Z")
            .andExpect(status().isOk());
    assertThat(read(fromAfter, "$.appointments[*].id"))
        .containsExactly(second.toString(), third.toString());
  }

  @Test
  void limitOutOfRangeIsRejected() throws Exception {
    call(alice, "/api/patients/" + theo + "/appointments?limit=0")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("limit"))
        .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));
    call(alice, "/api/patients/" + theo + "/appointments?limit=101")
        .andExpect(status().isUnprocessableContent());
  }

  // ---- lecture seule

  @Test
  void readsNeverWriteAppointments() throws Exception {
    appointment(alice, theo, "2030-03-12T08:00:00Z", 50);
    long before = appointmentCount();
    String updatedAt =
        jdbc.queryForObject(
            "select max(updated_at)::text from appointment where tenant_id = ?",
            String.class,
            tenant);

    call(alice, "/api/agenda?" + WEEK).andExpect(status().isOk());
    call(alice, "/api/agenda/freebusy?from=2030-03-12T08:00:00Z&to=2030-03-12T09:00:00Z")
        .andExpect(status().isOk());
    call(alice, "/api/patients/" + theo + "/appointments").andExpect(status().isOk());

    assertThat(appointmentCount()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "select max(updated_at)::text from appointment where tenant_id = ?",
                String.class,
                tenant))
        .isEqualTo(updatedAt);
  }
}
