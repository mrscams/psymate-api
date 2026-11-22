package com.psymate.agenda;

import static com.psymate.agenda.PublicBookingFixtures.applicantEmail;
import static com.psymate.agenda.PublicBookingFixtures.json;
import static com.psymate.agenda.PublicBookingFixtures.request;
import static com.psymate.agenda.PublicBookingFixtures.sha256;
import static com.psymate.agenda.PublicBookingFixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.agenda.PublicBookingFixtures.Practitioner;
import com.psymate.agenda.api.AppointmentPlanned;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * {@code POST /api/public/booking/requests/verify} (M14-06, G Q73, Q74, Q77, Q79, Q81, Q82 b) sur
 * une vraie base (Testcontainers pgvector/pgvector:pg16) et Mailpit : création du rendez-vous
 * invité selon le mode lu à la vérification, idempotence, jetons, créneau pris, rendez-vous déjà
 * actif, e-mails au demandeur et au praticien.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, SharedMailpit.class, PublicBookingFixtures.ListenerConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class PublicBookingVerifyIT {

  private static final Pattern VERIFY_LINK =
      Pattern.compile("/rendez-vous/confirmer/([A-Za-z0-9_-]+)");

  /** Champs exacts d'un PublicAppointmentDto : ni identité du demandeur, ni note. */
  private static final Set<String> PUBLIC_FIELDS =
      Set.of(
          "status",
          "cancelledBy",
          "startsAt",
          "endsAt",
          "durationMin",
          "modality",
          "practitioner",
          "canCancel",
          "manageToken");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private PublicBookingFixtures f;
  private PublicBookingMails mails;

  @BeforeEach
  void setUp() {
    f = new PublicBookingFixtures(mvc, jdbc);
    mails = new PublicBookingMails();
  }

  /** Demande par l'API, jeton lu dans l'e-mail de vérification. */
  private String requestByApi(Practitioner p, Instant startsAt, String email) throws Exception {
    f.postRequest(p.slug(), request(startsAt, email)).andExpect(status().isAccepted());
    String text = mails.awaitText(email, "Confirmez votre demande de rendez-vous");
    assertThat(text).as("e-mail de vérification").isNotNull();
    Matcher m = VERIFY_LINK.matcher(text);
    assertThat(m.find()).isTrue();
    return m.group(1);
  }

  private List<String> agendaIds(Practitioner p, String url) throws Exception {
    JsonNode body = json(f.practitionerGet(p, url).andExpect(status().isOk()).andReturn());
    List<String> ids = new ArrayList<>();
    body.path("appointments").forEach(a -> ids.add(a.path("id").asString()));
    return ids;
  }

  // ---- modes

  @Test
  void manualModeCreatesATentativeOnlineGuestAppointmentAndErasesTheIdentity(CapturedOutput output)
      throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    Instant startsAt = slot(3, "09:00");
    String window =
        "/api/agenda?from="
            + startsAt.minus(1, ChronoUnit.DAYS)
            + "&to="
            + startsAt.plus(1, ChronoUnit.DAYS);
    String token = requestByApi(p, startsAt, email);

    // Avant la vérification : rien dans l'agenda.
    assertThat(agendaIds(p, "/api/agenda/requests")).isEmpty();
    assertThat(agendaIds(p, window)).isEmpty();

    MvcResult result =
        f.verify(token)
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("pending"))
            .andExpect(jsonPath("$.cancelledBy").isEmpty())
            .andExpect(jsonPath("$.startsAt").value(startsAt.toString()))
            .andExpect(jsonPath("$.endsAt").value(startsAt.plus(60, ChronoUnit.MINUTES).toString()))
            .andExpect(jsonPath("$.durationMin").value(60))
            .andExpect(jsonPath("$.modality").value("video"))
            .andExpect(jsonPath("$.practitioner.slug").value(p.slug()))
            .andExpect(jsonPath("$.practitioner.displayName").value(p.displayName()))
            .andExpect(jsonPath("$.canCancel").value(true))
            .andReturn();
    JsonNode dto = json(result);
    String manageToken = dto.path("manageToken").asString();
    assertThat(manageToken).hasSize(43);
    assertThat(new HashSet<>(dto.propertyNames())).isEqualTo(PUBLIC_FIELDS);
    assertThat(new HashSet<>(dto.path("practitioner").propertyNames()))
        .containsExactlyInAnyOrder("slug", "displayName");
    assertThat(PublicBookingFixtures.body(result))
        .doesNotContain("Sara", "Benali", email, "+33", "planningNote");

    Map<String, Object> request = f.requestRow(token);
    assertThat(request.get("verified_at")).isNotNull();
    assertThat(request.get("first_name")).isNull();
    assertThat(request.get("last_name")).isNull();
    assertThat(request.get("email")).isNull();
    assertThat(request.get("phone")).isNull();
    assertThat(request.get("manage_token_hash")).isEqualTo(sha256(manageToken));
    assertThat(request.values().stream().map(String::valueOf))
        .noneMatch(v -> v.contains(token) || v.contains(manageToken));
    Map<String, Object> appointment = f.appointmentRow(request.get("appointment_id"));
    assertThat(appointment)
        .containsEntry("status", "tentative")
        .containsEntry("origin", "online")
        .containsEntry("patient_id", null)
        .containsEntry("guest_first_name", "Sara")
        .containsEntry("guest_last_name", "Benali")
        .containsEntry("guest_phone", "+33 6 12 34 56 78")
        .containsEntry("guest_email", email)
        .containsEntry("modality", "video")
        .containsEntry("planning_note", null)
        .containsEntry("practitioner_id", p.id())
        .containsEntry("tenant_id", f.tenant);
    assertThat(f.publications(AppointmentPlanned.class, request.get("appointment_id")))
        .isEqualTo(1);

    // Après la vérification : la demande apparaît chez le praticien.
    String appointmentId = request.get("appointment_id").toString();
    assertThat(agendaIds(p, "/api/agenda/requests")).containsExactly(appointmentId);
    assertThat(agendaIds(p, window)).containsExactly(appointmentId);

    String toApplicant = mails.awaitText(email, "Votre demande de rendez-vous");
    assertThat(toApplicant)
        .isNotNull()
        .contains("/rendez-vous/gerer/" + manageToken)
        .contains(p.displayName())
        .contains("PsyMate n'est pas un service d'urgence.");
    String toPractitioner = mails.awaitText(p.email(), "Nouvelle demande de rendez-vous");
    assertThat(toPractitioner)
        .isNotNull()
        .contains("Sara Benali")
        .contains("/agenda")
        .doesNotContain(manageToken);

    // Les journaux ne portent ni jeton, ni nom, ni adresse, ni téléphone.
    assertThat(output.getAll())
        .doesNotContain(token)
        .doesNotContain(manageToken)
        .doesNotContain(email)
        .doesNotContain("Benali")
        .doesNotContain("12 34 56 78");
  }

  @Test
  void autoModeCreatesAPlannedAppointment() throws Exception {
    Practitioner p = f.practitioner("auto");
    String email = applicantEmail();
    String token = requestByApi(p, slot(3, "10:00"), email);

    f.verify(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("confirmed"))
        .andExpect(jsonPath("$.canCancel").value(true));

    Map<String, Object> request = f.requestRow(token);
    assertThat(f.appointmentRow(request.get("appointment_id")))
        .containsEntry("status", "planned")
        .containsEntry("origin", "online");
    assertThat(mails.awaitText(email, "Votre rendez-vous")).isNotNull().contains("est confirmé");
    assertThat(mails.awaitText(p.email(), "Nouveau rendez-vous pris en ligne"))
        .isNotNull()
        .contains("Sara Benali");
    assertThat(agendaIds(p, "/api/agenda/requests")).isEmpty();
  }

  @Test
  void theModeAppliedIsTheOneReadAtVerification() throws Exception {
    Practitioner p = f.practitioner("manual");
    String token = f.pendingRequest(p, slot(4, "09:00"), applicantEmail());
    f.setPreference(p.id(), "booking.confirmation", "auto");

    f.verify(token).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("confirmed"));
    assertThat(f.appointmentRow(f.requestRow(token).get("appointment_id")))
        .containsEntry("status", "planned");
  }

  // ---- idempotence et jetons

  @Test
  void secondVerificationReturnsTheSameAppointmentWithoutManageTokenNorEmail() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    String token = requestByApi(p, slot(3, "11:00"), email);
    JsonNode first = json(f.verify(token).andExpect(status().isOk()).andReturn());
    // Le premier e-mail est parti avant le second appel.
    assertThat(mails.awaitText(email, "Votre demande de rendez-vous")).isNotNull();
    assertThat(mails.awaitText(p.email(), "Nouvelle demande de rendez-vous")).isNotNull();

    JsonNode second = json(f.verify(token).andExpect(status().isOk()).andReturn());

    assertThat(second.path("manageToken").isNull()).isTrue();
    assertThat(second.path("status").asString()).isEqualTo("pending");
    assertThat(second.path("startsAt")).isEqualTo(first.path("startsAt"));
    assertThat(f.appointmentsOf(p.id())).isEqualTo(1);
    assertThat(mails.countAfterQuiet(email, "Votre demande de rendez-vous")).isEqualTo(1);
    assertThat(mails.countAfterQuiet(p.email(), "Nouvelle demande de rendez-vous")).isEqualTo(1);
  }

  @Test
  void unknownTokenIs404AndExpiredTokenIs410() throws Exception {
    Practitioner p = f.practitioner("manual");
    f.verify(PublicBookingFixtures.newToken())
        .andExpect(status().isNotFound())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("BOOKING_TOKEN_INVALID"))
        .andExpect(jsonPath("$.instance").value("/api/public/booking"));

    // Lien émis il y a 31 minutes : expiré depuis 1 minute.
    String old =
        f.pendingRequest(
            p, slot(3, "09:00"), applicantEmail(), Instant.now().minus(1, ChronoUnit.MINUTES));
    f.verify(old)
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.code").value("BOOKING_TOKEN_EXPIRED"));
    assertThat(f.appointmentsOf(p.id())).isZero();

    f.verify("")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("token"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
  }

  @Test
  void featureNoLongerOfferedAtVerificationIs404BookingNotAvailable() throws Exception {
    Practitioner p = f.practitioner("manual");
    String token = f.pendingRequest(p, slot(3, "09:00"), applicantEmail());
    f.setPreference(p.id(), "booking.enabled", false);

    f.verify(token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("BOOKING_NOT_AVAILABLE"));
    assertThat(f.appointmentsOf(p.id())).isZero();
  }

  // ---- créneau exclusif (Q77)

  @Test
  void secondVerifiedRequestForTheSameSlotIs409SlotTaken() throws Exception {
    Practitioner p = f.practitioner("manual");
    Instant startsAt = slot(5, "09:00");
    String first = f.pendingRequest(p, startsAt, applicantEmail());
    String second = f.pendingRequest(p, startsAt, applicantEmail());

    f.verify(first).andExpect(status().isOk());
    f.verify(second)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));

    assertThat(f.appointmentsOf(p.id())).isEqualTo(1);
    Map<String, Object> left = f.requestRow(second);
    assertThat(left.get("verified_at")).isNull();
    assertThat(left.get("appointment_id")).isNull();
    assertThat(left.get("last_name")).isEqualTo("Benali");
  }

  @Test
  void twoParallelVerificationsForTheSameSlotCreateExactlyOneAppointment() throws Exception {
    Practitioner p = f.practitioner("auto");
    Instant startsAt = slot(5, "10:00");
    List<String> tokens =
        List.of(
            f.pendingRequest(p, startsAt, applicantEmail()),
            f.pendingRequest(p, startsAt, applicantEmail()));
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (String token : tokens) {
        Callable<Integer> call =
            () -> {
              start.await();
              return f.verify(token).andReturn().getResponse().getStatus();
            };
        results.add(pool.submit(call));
      }
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> r : results) {
        statuses.add(r.get(30, TimeUnit.SECONDS));
      }
      assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    } finally {
      pool.shutdownNow();
    }
    assertThat(f.appointmentsOf(p.id())).isEqualTo(1);
  }

  @Test
  void appointmentPlacedByThePractitionerBeforeVerificationIs409SlotTaken() throws Exception {
    Practitioner p = f.practitioner("manual");
    Instant startsAt = slot(6, "09:00");
    String token = f.pendingRequest(p, startsAt, applicantEmail());
    f.practitionerPost(
            p,
            "/api/appointments",
            "{\"startsAt\":\""
                + startsAt.plus(30, ChronoUnit.MINUTES)
                + "\",\"durationMin\":50,\"modality\":\"in_person\",\"guest\":{\"lastName\":\"Autre\"}}")
        .andExpect(status().isCreated());

    f.verify(token)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
    assertThat(f.appointmentsOf(p.id())).isEqualTo(1);
  }

  // ---- un seul rendez-vous en ligne actif et à venir par adresse et par praticien (Q79)

  @Test
  void sameAddressSecondSlotIs409AlreadyActiveButAcceptedAtAnotherPractitioner() throws Exception {
    Practitioner p = f.practitioner("manual");
    Practitioner other = f.practitioner("manual");
    String email = applicantEmail();
    f.verifiedRequest(p, slot(7, "09:00"), email);

    String again = f.pendingRequest(p, slot(8, "09:00"), " " + email.toUpperCase());
    f.verify(again)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_ACTIVE"));
    assertThat(f.appointmentsOf(p.id())).isEqualTo(1);
    assertThat(f.requestRow(again).get("verified_at")).isNull();

    String elsewhere = f.pendingRequest(other, slot(8, "09:00"), email);
    f.verify(elsewhere).andExpect(status().isOk());
    assertThat(f.appointmentsOf(other.id())).isEqualTo(1);
  }

  @Test
  void cancelledOrPastOnlineAppointmentDoesNotBlockANewOne() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    String manage = f.verifiedRequest(p, slot(7, "10:00"), email);
    f.cancel(manage).andExpect(status().isOk());

    f.verify(f.pendingRequest(p, slot(9, "10:00"), email)).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment where practitioner_id = ? and status ="
                    + " 'tentative'",
                Integer.class,
                p.id()))
        .isEqualTo(1);
    // La ligne vérifiée ne garde ni identité ni adresse en clair.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_request where practitioner_id = ? and email is not"
                    + " null and verified_at is not null",
                Integer.class,
                p.id()))
        .isZero();
  }
}
