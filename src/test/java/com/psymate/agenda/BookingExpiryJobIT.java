package com.psymate.agenda;

import static com.psymate.agenda.PublicBookingFixtures.applicantEmail;
import static com.psymate.agenda.PublicBookingFixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.agenda.PublicBookingFixtures.Practitioner;
import com.psymate.agenda.api.AppointmentCancelled;
import com.psymate.agenda.service.BookingExpiryJob;
import com.psymate.agenda.service.BookingMailer;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Expiration des demandes en ligne sans réponse (M14-07, G Q82, K3.3) sur une vraie base
 * (Testcontainers pgvector/pgvector:pg16) et Mailpit. Les délais passent par l'horloge injectée
 * ({@link BookingTestClock}) : la demande est vérifiée à l'heure réelle, puis l'application est
 * placée 71 h ou 73 h plus tard.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
  SharedPostgres.class,
  SharedMailpit.class,
  PublicBookingFixtures.ListenerConfig.class,
  BookingTestClock.class
})
class BookingExpiryJobIT {

  static final String EXPIRED = "Votre demande a expiré";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BookingMailer mailer;
  @Autowired private BookingExpiryJob job;
  @Autowired private BookingTestClock.OffsetClock clock;

  private PublicBookingFixtures f;
  private PublicBookingMails mails;

  @BeforeEach
  void setUp() {
    clock.reset();
    f = new PublicBookingFixtures(mvc, jdbc);
    mails = new PublicBookingMails();
  }

  @AfterEach
  void resetClock() {
    clock.reset();
  }

  @Test
  void defaultPendingDelayIsSeventyTwoHours() {
    assertThat(job.pendingTtl()).isEqualTo(Duration.ofHours(72));
  }

  @Test
  void requestVerifiedSeventyThreeHoursAgoExpiresOnceWithEventAndEmail() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    String token = f.verifiedRequest(p, slot(6, "09:00"), email);
    UUID id = f.appointmentOf(token);
    f.settle(id, mailer);

    clock.advance(Duration.ofHours(73));
    assertThat(job.run()).isGreaterThanOrEqualTo(1);
    f.settle(id, mailer);

    Map<String, Object> state = f.state(id);
    assertThat(state.get("status")).isEqualTo("cancelled");
    assertThat(state.get("cancel_reason")).isEqualTo("Demande expirée");
    assertThat(state.get("cancelled_at")).isNotNull();
    assertThat(state.get("closed_by")).isEqualTo("expired");
    assertThat(f.publications(AppointmentCancelled.class, id)).isEqualTo(1);
    String text = mails.awaitText(email, EXPIRED);
    assertThat(text).as("e-mail d'expiration").isNotNull();
    assertThat(EXPIRED).doesNotContain("Lambert").doesNotContainPattern("[0-9]");
    assertThat(text)
        .contains(p.displayName())
        .contains("PsyMate n'est pas un service d'urgence.")
        .doesNotContain("/rendez-vous/gerer/")
        .doesNotContain("Demande expirée");
    f.manage(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.cancelledBy").value("expired"));

    // Second passage : aucun effet, aucun e-mail.
    Object cancelledAt = state.get("cancelled_at");
    job.run();
    f.settle(id, mailer);
    assertThat(f.state(id).get("cancelled_at")).isEqualTo(cancelledAt);
    assertThat(f.publications(AppointmentCancelled.class, id)).isEqualTo(1);
    assertThat(mails.countAfterQuiet(email, EXPIRED)).isEqualTo(1);
  }

  @Test
  void requestVerifiedSeventyOneHoursAgoIsUntouched() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    UUID id = f.appointmentOf(f.verifiedRequest(p, slot(6, "10:00"), email));
    f.settle(id, mailer);

    clock.advance(Duration.ofHours(71));
    job.run();
    f.settle(id, mailer);

    Map<String, Object> state = f.state(id);
    assertThat(state.get("status")).isEqualTo("tentative");
    assertThat(state.get("closed_by")).isNull();
    assertThat(f.publications(AppointmentCancelled.class, id)).isZero();
    assertThat(mails.countAfterQuiet(email, EXPIRED)).isZero();
  }

  @Test
  void requestWhoseSlotHasStartedExpiresBeforeTheDelay() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    // Demain matin : au plus 33 h d'ici, bien moins que 72 h après la vérification.
    Instant startsAt = slot(1, "09:00");
    UUID id = f.appointmentOf(f.verifiedRequest(p, startsAt, email));
    f.settle(id, mailer);

    clock.advance(Duration.between(Instant.now(), startsAt).plusMinutes(1));
    assertThat(job.run()).isGreaterThanOrEqualTo(1);
    f.settle(id, mailer);

    Map<String, Object> state = f.state(id);
    assertThat(state.get("status")).isEqualTo("cancelled");
    assertThat(state.get("closed_by")).isEqualTo("expired");
    assertThat(mails.awaitCount(email, EXPIRED, 1)).isEqualTo(1);
  }

  @Test
  void confirmedRequestNeverExpires() throws Exception {
    Practitioner manual = f.practitioner("manual");
    String confirmedEmail = applicantEmail();
    UUID confirmed = f.appointmentOf(f.verifiedRequest(manual, slot(5, "09:00"), confirmedEmail));
    f.practitionerPost(manual, "/api/appointments/" + confirmed + "/confirm", "{}")
        .andExpect(status().isOk());
    Practitioner auto = f.practitioner("auto");
    String autoEmail = applicantEmail();
    UUID planned = f.appointmentOf(f.verifiedRequest(auto, slot(5, "10:00"), autoEmail));
    f.settle(confirmed, mailer);
    f.settle(planned, mailer);

    // Bien après la vérification et après le début du créneau.
    clock.advance(Duration.ofDays(10));
    job.run();
    f.settle(confirmed, mailer);
    f.settle(planned, mailer);

    assertThat(f.state(confirmed).get("status")).isEqualTo("planned");
    assertThat(f.state(confirmed).get("closed_by")).isNull();
    assertThat(f.state(planned).get("status")).isEqualTo("planned");
    assertThat(f.state(planned).get("closed_by")).isNull();
    assertThat(mails.countAfterQuiet(confirmedEmail, EXPIRED)).isZero();
    assertThat(mails.countAfterQuiet(autoEmail, EXPIRED)).isZero();
  }

  @Test
  void tentativeCreatedByThePractitionerNeverExpires() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    String body =
        "{\"startsAt\":\""
            + slot(2, "09:00")
            + "\",\"durationMin\":50,\"modality\":\"in_person\",\"tentative\":true,"
            + "\"guest\":{\"lastName\":\"Benali\",\"email\":\""
            + email
            + "\"}}";
    String created =
        PublicBookingFixtures.body(
            f.practitionerPost(p, "/api/appointments", body)
                .andExpect(status().isCreated())
                .andReturn());
    UUID id = UUID.fromString(JsonPath.read(created, "$.appointments[0].id"));

    clock.advance(Duration.ofDays(10));
    job.run();

    assertThat(f.appointmentRow(id).get("status")).isEqualTo("tentative");
    assertThat(mails.countAllAfterQuiet(email)).isZero();
  }

  @Test
  void attachPatientAndExpiryInParallelNeverLoseTheAttachment() throws Exception {
    for (int round = 0; round < 3; round++) {
      clock.reset();
      Practitioner p = f.practitioner("manual");
      String email = applicantEmail();
      UUID id = f.appointmentOf(f.verifiedRequest(p, slot(6 + round, "09:00"), email));
      UUID patient = UUID.randomUUID();
      jdbc.update(
          "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'S. B.')",
          patient,
          f.tenant,
          p.id());
      f.settle(id, mailer);
      clock.advance(Duration.ofHours(73));
      boolean sameGuest = round % 2 == 1;

      CountDownLatch start = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        Future<Integer> attach =
            pool.submit(
                () -> {
                  start.await();
                  return f.practitionerPost(
                          p,
                          "/api/appointments/" + id + "/attach-patient",
                          "{\"patientId\":\""
                              + patient
                              + "\",\"includeSameGuest\":"
                              + sameGuest
                              + "}")
                      .andReturn()
                      .getResponse()
                      .getStatus();
                });
        Future<Integer> expiry =
            pool.submit(
                () -> {
                  start.await();
                  return job.run();
                });
        start.countDown();
        assertThat(attach.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        expiry.get(30, TimeUnit.SECONDS);
      } finally {
        pool.shutdownNow();
      }
      f.settle(id, mailer);

      Map<String, Object> row = f.appointmentRow(id);
      assertThat(row.get("patient_id")).as("rattachement gardé").isEqualTo(patient);
      assertThat(row.get("guest_last_name")).isNull();
      assertThat(row.get("guest_email")).isNull();
      // L'expiration passe dans tous les cas, avant ou après le rattachement.
      assertThat(row.get("status")).isEqualTo("cancelled");
      assertThat(f.state(id).get("closed_by")).isEqualTo("expired");
      assertThat(f.publications(AppointmentCancelled.class, id)).isEqualTo(1);
    }
  }

  @Test
  void expiryNeverDeletesAnAppointment() throws Exception {
    Practitioner p = f.practitioner("manual");
    f.verifiedRequest(p, slot(6, "11:00"), applicantEmail());
    // Base partagée (B1) : on ne compte que les rendez-vous du praticien du test.
    assertThat(f.appointmentsOf(p.id())).isEqualTo(1);

    clock.advance(Duration.ofHours(73).plus(1, ChronoUnit.MINUTES));
    job.run();

    assertThat(f.appointmentsOf(p.id())).isEqualTo(1);
  }
}
