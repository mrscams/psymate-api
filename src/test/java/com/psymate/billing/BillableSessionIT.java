package com.psymate.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.clinical.api.EntryArchived;
import com.psymate.clinical.api.SessionClosed;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Séances à facturer (M15-07, G Q86, Q87, L5.2) sur une vraie base (Testcontainers
 * pgvector/pgvector:pg16) : écoute de SessionClosed et de EntryArchived (rejeu compris), séances
 * d'accueil et de contact jamais listées, création à la demande avant le traitement de l'événement,
 * « ne pas facturer » et « remettre », tarif et rateSource dans les quatre cas, isolement entre
 * praticiens, chaque code d'erreur.
 *
 * <p>Les séances du chemin réel sont ouvertes et closes par les routes de clinical ; les séances
 * des tests de liste et de tarif sont des lignes {@code billable_session} écrites en SQL (table du
 * module, dates maîtrisées).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class BillableSessionIT {

  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
  private static final Duration WAIT = Duration.ofSeconds(30);
  private static final String LIST = "/api/billing/billable-sessions";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DomainEventPublisher publisher;
  @Autowired private TransactionTemplate tx;
  @Autowired private Clock clock;

  private UUID tenant;
  private UUID alice;
  private UUID bob;
  private UUID theo;
  private UUID bobsPatient;
  private LocalDate today;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet séances à facturer')", tenant);
    alice = practitioner();
    bob = practitioner();
    theo = patient(alice, "Théo B.");
    bobsPatient = patient(bob, "Patient de Bob");
    today = today();
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- écoute de SessionClosed

  @Test
  void aClosedSessionAppearsInTheListThroughTheSessionClosedListener() throws Exception {
    LocalDate before = today();
    UUID session = closedSession(alice, theo, "video", 50);

    awaitState(session, "pending");
    assertThat(
            jdbc.queryForMap("select * from billable_session where session_entry_id = ?", session))
        .containsEntry("practitioner_id", alice)
        .containsEntry("tenant_id", tenant)
        .containsEntry("patient_id", theo)
        .containsEntry("modality", "video")
        .containsEntry("duration_min", 50);
    LocalDate day = occurredOn(session);
    assertIsTheDayOfTheRequest(day, before);
    list(alice, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups", hasSize(1)))
        .andExpect(jsonPath("$.groups[0].patient.id").value(theo.toString()))
        .andExpect(jsonPath("$.groups[0].patient.displayName").value("Théo B."))
        .andExpect(jsonPath("$.groups[0].patient.archived").value(false))
        .andExpect(jsonPath("$.groups[0].openDraftCount").value(0))
        .andExpect(jsonPath("$.groups[0].lastInvoice").value(nullValue()))
        .andExpect(jsonPath("$.groups[0].sessions[0].sessionEntryId").value(session.toString()))
        .andExpect(jsonPath("$.groups[0].sessions[0].occurredOn").value(day.toString()))
        .andExpect(jsonPath("$.groups[0].sessions[0].modality").value("video"))
        .andExpect(jsonPath("$.groups[0].sessions[0].durationMin").value(50))
        .andExpect(jsonPath("$.dismissedCount").value(0));
  }

  @Test
  void intakeAndContactSessionsAreNeverListed() throws Exception {
    UUID patient = createPatientThroughTheApi(alice, "Nora L.");
    UUID intake = awaitIntake(patient);
    perform(
            MockMvcRequestBuilders.post("/api/sessions/" + intake + "/close"),
            alice,
            "{\"durationMin\":45}")
        .andExpect(status().isOk());
    UUID contact = recordContact(alice, patient);
    UUID session = closedSession(alice, patient, "in_person", 50);

    awaitState(session, "pending");
    awaitCompleted(BillableSessionListenerIds.SESSION_CLOSED, intake);
    awaitCompleted(BillableSessionListenerIds.SESSION_CLOSED, contact);
    assertThat(rows(intake)).isZero();
    assertThat(rows(contact)).isZero();
    list(alice, "?patientId=" + patient)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups[0].sessions", hasSize(1)))
        .andExpect(jsonPath("$.groups[0].sessions[0].sessionEntryId").value(session.toString()));
    // Pas de création à la demande non plus.
    expectNotBillable(dismiss(alice, intake));
    expectNotBillable(dismiss(alice, contact));
    expectNotBillable(patch(alice, contact, "{\"amount\":5000}"));
    assertThat(rows(intake) + rows(contact)).isZero();
  }

  @Test
  void replayingSessionClosedCreatesNoDuplicateAndNeverWakesADismissedSession() throws Exception {
    UUID session = closedSession(alice, theo, "video", 50);
    awaitState(session, "pending");

    replay(sessionClosed(session, theo, alice, "session"));
    assertThat(rows(session)).isEqualTo(1);
    assertThat(state(session)).isEqualTo("pending");

    dismiss(alice, session).andExpect(status().isNoContent());
    replay(sessionClosed(session, theo, alice, "session"));
    assertThat(rows(session)).isEqualTo(1);
    assertThat(state(session)).isEqualTo("dismissed");
  }

  // ---- écoute de EntryArchived

  @Test
  void archivingAPendingSessionDismissesItThroughTheEntryArchivedListener() throws Exception {
    UUID session = closedSession(alice, theo, "in_person", 50);
    awaitState(session, "pending");

    perform(MockMvcRequestBuilders.delete("/api/entries/" + session), alice, null)
        .andExpect(status().isNoContent());

    awaitState(session, "dismissed");
    list(alice, "").andExpect(jsonPath("$.groups", hasSize(0)));
    dismissedList(alice)
        .andExpect(jsonPath("$.sessions", hasSize(1)))
        .andExpect(jsonPath("$.sessions[0].sessionEntryId").value(session.toString()));
    // Une séance archivée ne se remet pas à facturer.
    expectNotBillable(restore(alice, session));
    assertThat(state(session)).isEqualTo("dismissed");
  }

  @Test
  void aSessionClosedHandledAfterTheArchivingCreatesNoPendingSession() throws Exception {
    // EntryArchived traité avant SessionClosed (ordre non garanti, republication) : la séance
    // archivée ne naît pas à facturer.
    UUID session = closedSession(alice, theo, "video", 50);
    awaitState(session, "pending");
    awaitCompleted(BillableSessionListenerIds.SESSION_CLOSED, session);
    jdbc.update("delete from billable_session where session_entry_id = ?", session);
    perform(MockMvcRequestBuilders.delete("/api/entries/" + session), alice, null)
        .andExpect(status().isNoContent());
    awaitCompleted(BillableSessionListenerIds.ENTRY_ARCHIVED, session);
    assertThat(rows(session)).isZero();

    replay(sessionClosed(session, theo, alice, "session"));

    assertThat(rows(session)).isZero();
    list(alice, "").andExpect(jsonPath("$.groups", hasSize(0)));
    expectNotBillable(dismiss(alice, session));
  }

  @Test
  void replayingEntryArchivedOnlyDismissesPendingSessions() throws Exception {
    UUID pending = row(alice, theo, today, "pending");
    jdbc.update(
        "update billable_session set amount_cents = 7000 where session_entry_id = ?", pending);
    UUID invoiced = row(alice, theo, today, "pending");
    invoice(invoiced, "draft");
    UUID note = UUID.randomUUID();

    replay(new EntryArchived(tenant, pending, theo, "session", 2));
    replay(new EntryArchived(tenant, pending, theo, "session", 2));
    replay(new EntryArchived(tenant, invoiced, theo, "session", 2));
    replay(new EntryArchived(tenant, note, theo, "note", 1));

    assertThat(state(pending)).isEqualTo("dismissed");
    // Mise à jour conditionnelle : seuls l'état et la date changent.
    assertThat(
            jdbc.queryForObject(
                "select amount_cents from billable_session where session_entry_id = ?",
                Integer.class,
                pending))
        .isEqualTo(7000);
    assertThat(state(invoiced)).isEqualTo("invoiced");
    assertThat(
            jdbc.queryForObject(
                "select invoice_id is not null from billable_session where session_entry_id = ?",
                Boolean.class,
                invoiced))
        .isTrue();
    assertThat(rows(note)).isZero();
  }

  // ---- création à la demande (G Q86)

  @Test
  void dismissRightAfterClosureSucceedsBeforeTheEventIsHandled() throws Exception {
    // Événement pas encore traité : la ligne n'existe pas (simulée en la retirant).
    LocalDate before = today();
    UUID session = closedSession(alice, theo, "video", 50);
    awaitState(session, "pending");
    awaitCompleted(BillableSessionListenerIds.SESSION_CLOSED, session);
    jdbc.update("delete from billable_session where session_entry_id = ?", session);

    dismiss(alice, session).andExpect(status().isNoContent());
    assertThat(state(session)).isEqualTo("dismissed");
    assertThat(
            jdbc.queryForMap(
                "select modality, duration_min, patient_id from billable_session"
                    + " where session_entry_id = ?",
                session))
        .containsEntry("modality", "video")
        .containsEntry("duration_min", 50)
        .containsEntry("patient_id", theo);
    assertIsTheDayOfTheRequest(occurredOn(session), before);
    // L'événement traité ensuite ne remet pas la séance à facturer.
    replay(sessionClosed(session, theo, alice, "session"));
    assertThat(state(session)).isEqualTo("dismissed");

    // Course réelle : « ne pas facturer » aussitôt la clôture, sans attendre l'écoute.
    UUID raced = closedSession(alice, theo, "phone", 30);
    dismiss(alice, raced).andExpect(status().isNoContent());
    awaitCompleted(BillableSessionListenerIds.SESSION_CLOSED, raced);
    assertThat(rows(raced)).isEqualTo(1);
    assertThat(state(raced)).isEqualTo("dismissed");
  }

  @Test
  void patchAndRestoreAlsoCreateTheSessionOnDemand() throws Exception {
    UUID first = closedSession(alice, theo, "video", 50);
    awaitState(first, "pending");
    awaitCompleted(BillableSessionListenerIds.SESSION_CLOSED, first);
    jdbc.update("delete from billable_session where session_entry_id = ?", first);

    patch(alice, first, "{\"amount\":7000}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionEntryId").value(first.toString()))
        .andExpect(jsonPath("$.amount").value(7000))
        .andExpect(jsonPath("$.rateSource").value("session"));
    assertThat(state(first)).isEqualTo("pending");

    jdbc.update("delete from billable_session where session_entry_id = ?", first);
    restore(alice, first).andExpect(status().isNoContent());
    assertThat(state(first)).isEqualTo("pending");
  }

  // ---- ne pas facturer, remettre

  @Test
  void dismissThenRestore() throws Exception {
    // Séance réelle : « remettre » vérifie auprès de clinical que la séance n'est pas archivée.
    UUID session = closedSession(alice, theo, "in_person", 50);
    awaitState(session, "pending");

    dismiss(alice, session).andExpect(status().isNoContent());
    dismiss(alice, session).andExpect(status().isNoContent());
    assertThat(state(session)).isEqualTo("dismissed");
    list(alice, "")
        .andExpect(jsonPath("$.groups", hasSize(0)))
        .andExpect(jsonPath("$.dismissedCount").value(1));
    dismissedList(alice)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessions", hasSize(1)))
        .andExpect(jsonPath("$.sessions[0].sessionEntryId").value(session.toString()))
        .andExpect(jsonPath("$.sessions[0].patient.id").value(theo.toString()))
        .andExpect(jsonPath("$.sessions[0].patient.displayName").value("Théo B."))
        .andExpect(jsonPath("$.sessions[0].rateSource").value("none"));

    restore(alice, session).andExpect(status().isNoContent());
    restore(alice, session).andExpect(status().isNoContent());
    assertThat(state(session)).isEqualTo("pending");
    list(alice, "")
        .andExpect(jsonPath("$.groups[0].sessions[0].sessionEntryId").value(session.toString()))
        .andExpect(jsonPath("$.dismissedCount").value(0));
    dismissedList(alice).andExpect(jsonPath("$.sessions", hasSize(0)));
  }

  @Test
  void dismissedListCoversTheLast90DaysMostRecentFirst() throws Exception {
    onOneParisDay(
        day -> {
          UUID owner = practitioner();
          UUID patient = patient(owner, "Fenêtre");
          UUID recent = row(owner, patient, day.minusDays(2), "dismissed");
          UUID edge = row(owner, patient, day.minusDays(90), "dismissed");
          UUID old = row(owner, patient, day.minusDays(91), "dismissed");

          dismissedList(owner)
              .andExpect(jsonPath("$.sessions", hasSize(2)))
              .andExpect(jsonPath("$.sessions[0].sessionEntryId").value(recent.toString()))
              .andExpect(jsonPath("$.sessions[1].sessionEntryId").value(edge.toString()));
          list(owner, "").andExpect(jsonPath("$.dismissedCount").value(2));
          assertThat(rows(old)).isEqualTo(1);
        });
  }

  @Test
  void dismissedCountFollowsThePatientFilter() throws Exception {
    onOneParisDay(
        day -> {
          UUID owner = practitioner();
          UUID first = patient(owner, "Premier");
          UUID second = patient(owner, "Second");
          row(owner, first, day, "pending");
          row(owner, first, day.minusDays(1), "dismissed");
          row(owner, second, day, "pending");
          row(owner, second, day.minusDays(3), "dismissed");
          row(owner, second, day.minusDays(4), "dismissed");
          row(owner, second, day.minusDays(91), "dismissed");

          list(owner, "").andExpect(jsonPath("$.dismissedCount").value(3));
          list(owner, "?patientId=" + first)
              .andExpect(jsonPath("$.groups", hasSize(1)))
              .andExpect(jsonPath("$.dismissedCount").value(1));
          list(owner, "?patientId=" + second)
              .andExpect(jsonPath("$.groups", hasSize(1)))
              .andExpect(jsonPath("$.dismissedCount").value(2));
          // until ne restreint pas dismissedCount.
          list(owner, "?patientId=" + second + "&until=" + day.minusDays(10))
              .andExpect(jsonPath("$.groups", hasSize(0)))
              .andExpect(jsonPath("$.dismissedCount").value(2));
        });
  }

  // ---- tarif (G Q87)

  @Test
  void rateSourceIsSessionPatientServiceOrNone() throws Exception {
    UUID session = row(alice, theo, today, "pending");

    // none : ni montant, ni tarif du patient, ni prestation.
    list(alice, "")
        .andExpect(jsonPath("$.groups[0].rate").value(nullValue()))
        .andExpect(jsonPath("$.groups[0].rateSource").value("none"))
        .andExpect(jsonPath("$.groups[0].sessions[0].amount").value(nullValue()))
        .andExpect(jsonPath("$.groups[0].sessions[0].rateSource").value("none"))
        .andExpect(jsonPath("$.groups[0].sessions[0].serviceId").value(nullValue()));

    // service : prix de la prestation par défaut.
    String serviceId = createService(alice, "Séance individuelle", 6000, true);
    list(alice, "")
        .andExpect(jsonPath("$.groups[0].rate").value(6000))
        .andExpect(jsonPath("$.groups[0].rateSource").value("service"))
        .andExpect(jsonPath("$.groups[0].sessions[0].amount").value(6000))
        .andExpect(jsonPath("$.groups[0].sessions[0].rateSource").value("service"))
        .andExpect(jsonPath("$.groups[0].sessions[0].serviceId").value(serviceId))
        .andExpect(jsonPath("$.groups[0].sessions[0].serviceLabel").value("Séance individuelle"));

    // patient : le tarif du patient passe avant la prestation.
    perform(
            MockMvcRequestBuilders.put("/api/patients/" + theo + "/billing-profile"),
            alice,
            "{\"rate\":5500}")
        .andExpect(status().isOk());
    list(alice, "")
        .andExpect(jsonPath("$.groups[0].rate").value(5500))
        .andExpect(jsonPath("$.groups[0].rateSource").value("patient"))
        .andExpect(jsonPath("$.groups[0].sessions[0].amount").value(5500))
        .andExpect(jsonPath("$.groups[0].sessions[0].rateSource").value("patient"));

    // session : le montant fixé sur la séance passe avant tout.
    patch(alice, session, "{\"amount\":9000}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(9000))
        .andExpect(jsonPath("$.rateSource").value("session"));
    list(alice, "")
        .andExpect(jsonPath("$.groups[0].rate").value(5500))
        .andExpect(jsonPath("$.groups[0].rateSource").value("session"))
        .andExpect(jsonPath("$.groups[0].sessions[0].amount").value(9000))
        .andExpect(jsonPath("$.groups[0].sessions[0].rateSource").value("session"));

    // null rend le tarif calculé.
    patch(alice, session, "{\"amount\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(5500))
        .andExpect(jsonPath("$.rateSource").value("patient"));
    assertThat(
            jdbc.queryForObject(
                "select amount_cents from billable_session where session_entry_id = ?",
                Integer.class,
                session))
        .isNull();
  }

  @Test
  void patchChoosesAServiceAndNullReturnsToTheDefault() throws Exception {
    UUID session = row(alice, theo, today, "pending");
    String standard = createService(alice, "Séance individuelle", 6000, true);
    String couple = createService(alice, "Séance de couple", 9000, false);

    patch(alice, session, "{\"serviceId\":\"" + couple + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceId").value(couple))
        .andExpect(jsonPath("$.serviceLabel").value("Séance de couple"))
        .andExpect(jsonPath("$.amount").value(9000))
        .andExpect(jsonPath("$.rateSource").value("service"));
    // Un champ absent reste inchangé.
    patch(alice, session, "{}").andExpect(jsonPath("$.serviceId").value(couple));

    patch(alice, session, "{\"serviceId\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceId").value(standard))
        .andExpect(jsonPath("$.amount").value(6000));
  }

  // ---- liste : filtres, ordre, factures

  @Test
  void listGroupsByDisplayNameSortsSessionsByDateAndFiltersUntilAndPatient() throws Exception {
    UUID emile = patient(alice, "Émile D.");
    UUID eric = patient(alice, "eric F.");
    UUID zoe = patient(alice, "Zoé A.");
    UUID late = row(alice, emile, today.minusDays(1), "pending");
    UUID early = row(alice, emile, today.minusDays(10), "pending");
    row(alice, eric, today.minusDays(5), "pending");
    UUID future = row(alice, zoe, today.plusDays(3), "pending");

    list(alice, "")
        .andExpect(jsonPath("$.groups", hasSize(3)))
        .andExpect(jsonPath("$.groups[0].patient.displayName").value("Émile D."))
        .andExpect(jsonPath("$.groups[1].patient.displayName").value("eric F."))
        .andExpect(jsonPath("$.groups[2].patient.displayName").value("Zoé A."))
        .andExpect(jsonPath("$.groups[0].sessions[0].sessionEntryId").value(early.toString()))
        .andExpect(jsonPath("$.groups[0].sessions[1].sessionEntryId").value(late.toString()));

    list(alice, "?until=" + today)
        .andExpect(jsonPath("$.groups", hasSize(2)))
        .andExpect(jsonPath("$.groups[1].patient.displayName").value("eric F."));
    list(alice, "?until=" + today.minusDays(5))
        .andExpect(jsonPath("$.groups", hasSize(2)))
        .andExpect(jsonPath("$.groups[0].sessions", hasSize(1)))
        .andExpect(jsonPath("$.groups[0].sessions[0].sessionEntryId").value(early.toString()));
    list(alice, "?patientId=" + zoe)
        .andExpect(jsonPath("$.groups", hasSize(1)))
        .andExpect(jsonPath("$.groups[0].sessions[0].sessionEntryId").value(future.toString()));
  }

  @Test
  void archivedPatientKeepsItsGroupAndDraftsAndLastInvoiceAreShown() throws Exception {
    UUID session = row(alice, theo, today, "pending");
    UUID drafted = row(alice, theo, today, "pending");
    invoice(drafted, "draft");
    issuedInvoice(theo, "F-2026-0001", today.minusDays(40), "issued");
    issuedInvoice(theo, "F-2026-0007", today.minusDays(10), "void");
    jdbc.update("update patient set archived_at = now() where id = ?", theo);

    list(alice, "")
        .andExpect(jsonPath("$.groups", hasSize(1)))
        .andExpect(jsonPath("$.groups[0].patient.archived").value(true))
        .andExpect(jsonPath("$.groups[0].openDraftCount").value(1))
        .andExpect(jsonPath("$.groups[0].lastInvoice.number").value("F-2026-0007"))
        .andExpect(
            jsonPath("$.groups[0].lastInvoice.issueDate").value(today.minusDays(10).toString()))
        .andExpect(jsonPath("$.groups[0].sessions", hasSize(1)))
        .andExpect(jsonPath("$.groups[0].sessions[0].sessionEntryId").value(session.toString()));
  }

  // ---- isolement

  @Test
  void practitionersAreIsolated() throws Exception {
    UUID bobsSession = closedSession(bob, bobsPatient, "video", 50);
    awaitState(bobsSession, "pending");
    UUID bobsRow = row(bob, bobsPatient, today, "dismissed");
    UUID alicesRow = row(alice, theo, today, "pending");
    String bobsService = createService(bob, "Séance de Bob", 8000, true);

    list(alice, "")
        .andExpect(jsonPath("$.groups", hasSize(1)))
        .andExpect(jsonPath("$.groups[0].patient.id").value(theo.toString()))
        .andExpect(jsonPath("$.groups[0].rateSource").value("none"))
        .andExpect(jsonPath("$.dismissedCount").value(0));
    dismissedList(alice).andExpect(jsonPath("$.sessions", hasSize(0)));
    list(alice, "?patientId=" + bobsPatient)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    expectNotBillable(dismiss(alice, bobsSession));
    expectNotBillable(restore(alice, bobsRow));
    expectNotBillable(patch(alice, bobsSession, "{\"amount\":100}"));
    expectFieldError(
        patch(alice, alicesRow, "{\"serviceId\":\"" + bobsService + "\"}"),
        "serviceId",
        "INVALID_VALUE");
    assertThat(state(bobsSession)).isEqualTo("pending");
    assertThat(state(bobsRow)).isEqualTo("dismissed");
    list(bob, "").andExpect(jsonPath("$.groups[0].patient.id").value(bobsPatient.toString()));
  }

  // ---- codes

  @Test
  void anOpenOrUnknownSessionIsNotBillable() throws Exception {
    UUID open = startSession(alice, theo, "video");

    expectNotBillable(dismiss(alice, open));
    expectNotBillable(restore(alice, open));
    expectNotBillable(patch(alice, open, "{\"amount\":6000}"));
    expectNotBillable(dismiss(alice, UUID.randomUUID()));
    assertThat(rows(open)).isZero();
  }

  @Test
  void anInvoicedSessionCannotBeDismissedRestoredNorChanged() throws Exception {
    UUID session = row(alice, theo, today, "pending");
    invoice(session, "draft");

    expectAlreadyInvoiced(dismiss(alice, session));
    expectAlreadyInvoiced(restore(alice, session));
    expectAlreadyInvoiced(patch(alice, session, "{\"amount\":6000}"));
    assertThat(state(session)).isEqualTo("invoiced");
    list(alice, "").andExpect(jsonPath("$.groups", hasSize(0)));
  }

  @Test
  void invalidAmountsServicesAndFieldsAre422() throws Exception {
    UUID session = row(alice, theo, today, "pending");
    String archived = createService(alice, "Ancienne", 5000, false);
    perform(MockMvcRequestBuilders.delete("/api/me/billing-services/" + archived), alice, null)
        .andExpect(status().isNoContent());

    expectFieldError(patch(alice, session, "{\"amount\":0}"), "amount", "OUT_OF_RANGE");
    expectFieldError(patch(alice, session, "{\"amount\":-5}"), "amount", "OUT_OF_RANGE");
    expectFieldError(patch(alice, session, "{\"amount\":60.5}"), "amount", "INVALID_FORMAT");
    expectFieldError(patch(alice, session, "{\"amount\":60.0}"), "amount", "INVALID_FORMAT");
    expectFieldError(patch(alice, session, "{\"amount\":\"6000\"}"), "amount", "INVALID_FORMAT");
    expectFieldError(
        patch(alice, session, "{\"serviceId\":\"" + archived + "\"}"),
        "serviceId",
        "INVALID_VALUE");
    expectFieldError(
        patch(alice, session, "{\"serviceId\":\"" + UUID.randomUUID() + "\"}"),
        "serviceId",
        "INVALID_VALUE");
    expectFieldError(
        patch(alice, session, "{\"serviceId\":\"pas-un-uuid\"}"), "serviceId", "INVALID_FORMAT");
    expectFieldError(patch(alice, session, "{\"state\":\"pending\"}"), "state", "UNKNOWN_FIELD");
    expectFieldError(list(alice, "?until=demain"), "until", "INVALID_FORMAT");
    expectFieldError(list(alice, "?patientId=pas-un-uuid"), "patientId", "INVALID_FORMAT");
    assertThat(
            jdbc.queryForObject(
                "select amount_cents is null and service_id is null from billable_session"
                    + " where session_entry_id = ?",
                Boolean.class,
                session))
        .isTrue();
  }

  @Test
  void malformedSessionIdIs400() throws Exception {
    perform(MockMvcRequestBuilders.post(LIST + "/pas-un-uuid/dismiss"), alice, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    perform(MockMvcRequestBuilders.patch(LIST + "/pas-un-uuid"), alice, "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void errorMessagesAreLocalized() throws Exception {
    UUID open = startSession(alice, theo, "video");
    for (String language : List.of("fr", "en", "ar")) {
      mvc.perform(
              MockMvcRequestBuilders.post(LIST + "/" + open + "/dismiss")
                  .header("Accept-Language", language)
                  .with(as(alice)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("SESSION_NOT_BILLABLE"))
          .andExpect(jsonPath("$.detail").isNotEmpty());
    }
  }

  // ---- outils : chemin réel de clinical

  private UUID startSession(UUID who, UUID patient, String modality) throws Exception {
    MvcResult result =
        perform(
                MockMvcRequestBuilders.post("/api/patients/" + patient + "/sessions/start"),
                who,
                "{\"modality\":\"" + modality + "\"}")
            .andExpect(status().isCreated())
            .andReturn();
    String location = result.getResponse().getHeader("Location");
    return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
  }

  private UUID closedSession(UUID who, UUID patient, String modality, int durationMin)
      throws Exception {
    UUID session = startSession(who, patient, modality);
    perform(
            MockMvcRequestBuilders.post("/api/sessions/" + session + "/close"),
            who,
            "{\"durationMin\":" + durationMin + "}")
        .andExpect(status().isOk());
    return session;
  }

  private UUID recordContact(UUID who, UUID patient) throws Exception {
    MvcResult result =
        perform(
                MockMvcRequestBuilders.post("/api/patients/" + patient + "/sessions/contact"),
                who,
                "{\"modality\":\"phone\",\"occurredFrom\":\""
                    + Instant.now().minusSeconds(600)
                    + "\",\"body\":\"Appel bref.\"}")
            .andExpect(status().isCreated())
            .andReturn();
    String location = result.getResponse().getHeader("Location");
    return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
  }

  private UUID createPatientThroughTheApi(UUID who, String displayName) throws Exception {
    MvcResult result =
        perform(
                MockMvcRequestBuilders.post("/api/patients"),
                who,
                "{\"displayName\":\"" + displayName + "\"}")
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(
        JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id"));
  }

  private UUID awaitIntake(UUID patient) {
    String sql =
        "select e.id from entry e join entry_session s on s.entry_id = e.id"
            + " where e.patient_id = ? and s.kind = 'intake'";
    await().atMost(WAIT).until(() -> !jdbc.queryForList(sql, UUID.class, patient).isEmpty());
    return jdbc.queryForObject(sql, UUID.class, patient);
  }

  private String createService(UUID who, String label, int price, boolean isDefault)
      throws Exception {
    MvcResult result =
        perform(
                MockMvcRequestBuilders.post("/api/me/billing-services"),
                who,
                "{\"label\":\""
                    + label
                    + "\",\"price\":"
                    + price
                    + ",\"isDefault\":"
                    + isDefault
                    + "}")
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
  }

  // ---- outils : événements

  private SessionClosed sessionClosed(UUID session, UUID patient, UUID owner, String kind) {
    Instant from = Instant.now().minusSeconds(3000);
    return new SessionClosed(
        tenant, session, patient, owner, kind, "video", from, Instant.now(), 50);
  }

  /** Publie une copie de l'événement (nouvel identifiant) et attend son traitement par billing. */
  private void replay(Object event) {
    tx.executeWithoutResult(s -> publisher.publish(event));
    String id = ((com.psymate.events.api.DomainEvent) event).getId().toString();
    await()
        .atMost(WAIT)
        .until(
            () ->
                jdbc.queryForObject(
                        "select count(*) from event_publication where listener_id like 'billing.%'"
                            + " and serialized_event like ? and completion_date is not null",
                        Integer.class, "%" + id + "%")
                    >= 1);
  }

  private void awaitCompleted(String listenerId, UUID aggregateId) {
    await()
        .atMost(WAIT)
        .until(
            () ->
                jdbc.queryForObject(
                        "select count(*) from event_publication where listener_id = ?"
                            + " and serialized_event like ? and completion_date is not null",
                        Integer.class,
                        listenerId,
                        "%" + aggregateId + "%")
                    >= 1);
  }

  private void awaitState(UUID session, String expected) {
    await().atMost(WAIT).until(() -> expected.equals(state(session)));
  }

  // ---- outils : jour de Paris, lu sur l'horloge injectée du serveur

  /** Corps de test qui dépend du jour exact. */
  @FunctionalInterface
  private interface DayBody {
    void run(LocalDate day) throws Exception;
  }

  /** Aujourd'hui à Paris selon l'horloge du contexte, celle que lit le serveur. */
  private LocalDate today() {
    return LocalDate.now(clock.withZone(PARIS));
  }

  /**
   * Exécute le corps sur un jour stable : s'il a franchi minuit pendant son exécution, il est
   * rejoué une fois (le corps crée ses propres praticien et patient, sans reste du premier
   * passage).
   */
  private void onOneParisDay(DayBody body) throws Exception {
    for (int attempt = 0; ; attempt++) {
      LocalDate day = today();
      body.run(day);
      if (day.equals(today()) || attempt == 1) {
        return;
      }
    }
  }

  /** Le jour d'une séance ouverte maintenant : celui d'avant la requête, ou d'après (minuit). */
  private void assertIsTheDayOfTheRequest(LocalDate day, LocalDate before) {
    assertThat(day).isIn(before, today());
  }

  private LocalDate occurredOn(UUID session) {
    return jdbc.queryForObject(
            "select occurred_on from billable_session where session_entry_id = ?",
            Date.class,
            session)
        .toLocalDate();
  }

  // ---- outils : base

  private UUID practitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Facturation', 'Dr Facturation',"
            + " 'psychologist')",
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

  /** Ligne billable_session écrite directement (séance fictive, date maîtrisée). */
  private UUID row(UUID owner, UUID patient, LocalDate occurredOn, String state) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into billable_session (session_entry_id, tenant_id, practitioner_id, patient_id,"
            + " occurred_on, modality, duration_min, state) values (?, ?, ?, ?, ?, 'in_person', 50,"
            + " ?)",
        id,
        tenant,
        owner,
        patient,
        Date.valueOf(occurredOn),
        state);
    return id;
  }

  /** Met la séance dans une facture (brouillon ou émise) écrite en SQL. */
  private void invoice(UUID session, String status) {
    UUID patient =
        jdbc.queryForObject(
            "select patient_id from billable_session where session_entry_id = ?",
            UUID.class,
            session);
    UUID owner =
        jdbc.queryForObject(
            "select practitioner_id from billable_session where session_entry_id = ?",
            UUID.class,
            session);
    UUID invoice = UUID.randomUUID();
    jdbc.update(
        "insert into invoice (id, tenant_id, practitioner_id, patient_id, kind, status,"
            + " issue_date, due_term) values (?, ?, ?, ?, 'invoice', ?, current_date,"
            + " 'on_receipt')",
        invoice,
        tenant,
        owner,
        patient,
        status);
    jdbc.update(
        "update billable_session set state = 'invoiced', invoice_id = ? where session_entry_id = ?",
        invoice,
        session);
  }

  private void issuedInvoice(UUID patient, String number, LocalDate issueDate, String status) {
    jdbc.update(
        "insert into invoice (id, tenant_id, practitioner_id, patient_id, kind, status, number,"
            + " issue_date, due_term, issuer_snapshot, issued_at) values (?, ?, ?, ?, 'invoice',"
            + " ?, ?, ?, 'on_receipt', '{}'::jsonb, now())",
        UUID.randomUUID(),
        tenant,
        alice,
        patient,
        status,
        number,
        Date.valueOf(issueDate));
  }

  private String state(UUID session) {
    List<String> states =
        jdbc.queryForList(
            "select state from billable_session where session_entry_id = ?", String.class, session);
    return states.isEmpty() ? null : states.getFirst();
  }

  private int rows(UUID session) {
    return jdbc.queryForObject(
        "select count(*) from billable_session where session_entry_id = ?", Integer.class, session);
  }

  // ---- outils : HTTP

  private static void expectNotBillable(ResultActions result) throws Exception {
    result
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_BILLABLE"))
        .andExpect(jsonPath("$.detail").isNotEmpty());
  }

  private static void expectAlreadyInvoiced(ResultActions result) throws Exception {
    result
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ALREADY_INVOICED"))
        .andExpect(jsonPath("$.detail").isNotEmpty());
  }

  private static void expectFieldError(ResultActions result, String field, String code)
      throws Exception {
    result
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].code").value(code))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
  }

  private RequestPostProcessor as(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            tenant,
            practitionerId + "@example.test",
            "Dr Facturation",
            "psychologist",
            "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions perform(MockHttpServletRequestBuilder builder, UUID who, String body)
      throws Exception {
    if (body != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.getBytes(StandardCharsets.UTF_8));
    }
    return mvc.perform(builder.with(as(who)));
  }

  private ResultActions list(UUID who, String query) throws Exception {
    return perform(MockMvcRequestBuilders.get(LIST + query), who, null);
  }

  private ResultActions dismissedList(UUID who) throws Exception {
    return perform(MockMvcRequestBuilders.get(LIST + "/dismissed"), who, null);
  }

  private ResultActions dismiss(UUID who, UUID session) throws Exception {
    return perform(MockMvcRequestBuilders.post(LIST + "/" + session + "/dismiss"), who, null);
  }

  private ResultActions restore(UUID who, UUID session) throws Exception {
    return perform(MockMvcRequestBuilders.post(LIST + "/" + session + "/restore"), who, null);
  }

  private ResultActions patch(UUID who, UUID session, String body) throws Exception {
    return perform(MockMvcRequestBuilders.patch(LIST + "/" + session), who, body);
  }

  /** Identifiants des écouteurs de billing, tels que stockés dans event_publication. */
  private static final class BillableSessionListenerIds {
    static final String SESSION_CLOSED = "billing.session-closed";
    static final String ENTRY_ARCHIVED = "billing.entry-archived";
  }
}
