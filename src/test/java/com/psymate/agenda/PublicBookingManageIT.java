package com.psymate.agenda;

import static com.psymate.agenda.PublicBookingFixtures.applicantEmail;
import static com.psymate.agenda.PublicBookingFixtures.json;
import static com.psymate.agenda.PublicBookingFixtures.sha256;
import static com.psymate.agenda.PublicBookingFixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.agenda.PublicBookingFixtures.Practitioner;
import com.psymate.agenda.api.AppointmentCancelled;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * {@code POST /api/public/booking/manage} et {@code …/manage/cancel} (M14-06, G Q80, K3.3) sur une
 * vraie base (Testcontainers pgvector/pgvector:pg16) et Mailpit : état du rendez-vous vu par le
 * demandeur, annulation par le demandeur et e-mail au praticien, clôture par le praticien.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, SharedMailpit.class, PublicBookingFixtures.ListenerConfig.class})
class PublicBookingManageIT {

  private static final String CANCELLED_SUBJECT = "Rendez-vous en ligne annulé";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private PublicBookingFixtures f;
  private PublicBookingMails mails;

  @BeforeEach
  void setUp() {
    f = new PublicBookingFixtures(mvc, jdbc);
    mails = new PublicBookingMails();
  }

  private UUID appointmentOf(String manageToken) throws Exception {
    return jdbc.queryForObject(
        "select appointment_id from booking_request where manage_token_hash = ?",
        UUID.class,
        sha256(manageToken));
  }

  private String closedBy(String manageToken) throws Exception {
    return jdbc.queryForObject(
        "select closed_by from booking_request where manage_token_hash = ?",
        String.class,
        sha256(manageToken));
  }

  /** Déplace le rendez-vous en SQL (passé, commencé). */
  private void move(UUID appointment, Instant startsAt, Instant endsAt) {
    jdbc.update(
        "update appointment set starts_at = ?, ends_at = ? where id = ?",
        Timestamp.from(startsAt),
        Timestamp.from(endsAt),
        appointment);
  }

  // ---- manage : état

  @Test
  void manageShowsPendingThenConfirmedThenCancelledByThePractitioner() throws Exception {
    Practitioner p = f.practitioner("manual");
    Instant startsAt = slot(3, "09:00");
    String token = f.verifiedRequest(p, startsAt, applicantEmail());
    UUID appointment = appointmentOf(token);

    MvcResult pending =
        f.manage(token)
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("pending"))
            .andExpect(jsonPath("$.canCancel").value(true))
            .andExpect(jsonPath("$.startsAt").value(startsAt.toString()))
            .andExpect(jsonPath("$.durationMin").value(60))
            .andExpect(jsonPath("$.modality").value("video"))
            .andExpect(jsonPath("$.practitioner.slug").value(p.slug()))
            .andReturn();
    JsonNode dto = json(pending);
    assertThat(dto.path("manageToken").isNull()).isTrue();
    assertThat(dto.path("cancelledBy").isNull()).isTrue();
    assertThat(new HashSet<>(dto.propertyNames()))
        .isEqualTo(
            Set.of(
                "status",
                "cancelledBy",
                "startsAt",
                "endsAt",
                "durationMin",
                "modality",
                "practitioner",
                "canCancel",
                "manageToken"));
    assertThat(PublicBookingFixtures.body(pending))
        .doesNotContain("Sara", "Benali", "@example.test", "planningNote");

    f.practitionerPost(p, "/api/appointments/" + appointment + "/confirm", "{}")
        .andExpect(status().isOk());
    f.manage(token)
        .andExpect(jsonPath("$.status").value("confirmed"))
        .andExpect(jsonPath("$.canCancel").value(true));

    f.practitionerPost(
            p, "/api/appointments/" + appointment + "/cancel", "{\"reason\":\"Indisponible\"}")
        .andExpect(status().isOk());
    MvcResult cancelled =
        f.manage(token)
            .andExpect(jsonPath("$.status").value("cancelled"))
            .andExpect(jsonPath("$.cancelledBy").value("practitioner"))
            .andExpect(jsonPath("$.canCancel").value(false))
            .andReturn();
    // Le motif du praticien n'est jamais montré au demandeur.
    assertThat(PublicBookingFixtures.body(cancelled)).doesNotContain("Indisponible");
    assertThat(closedBy(token)).isEqualTo("practitioner");
  }

  @Test
  void manageShowsPastForAnAppointmentThatHasEnded() throws Exception {
    Practitioner p = f.practitioner("auto");
    String token = f.verifiedRequest(p, slot(3, "10:00"), applicantEmail());
    Instant start = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    move(appointmentOf(token), start, start.plus(60, ChronoUnit.MINUTES));

    f.manage(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("past"))
        .andExpect(jsonPath("$.canCancel").value(false));
  }

  @Test
  void manageStillWorksWhenTheProfileIsNoLongerPublished() throws Exception {
    Practitioner p = f.practitioner("manual");
    String token = f.verifiedRequest(p, slot(3, "11:00"), applicantEmail());
    jdbc.update(
        "update practitioner_profile set published = false where practitioner_id = ?", p.id());

    f.manage(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"))
        .andExpect(jsonPath("$.practitioner.slug").isEmpty());
    f.cancel(token).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("cancelled"));
  }

  @Test
  void unknownOrVerificationTokenIs404() throws Exception {
    Practitioner p = f.practitioner("manual");
    String verifyToken = f.pendingRequest(p, slot(4, "09:00"), applicantEmail());
    f.verify(verifyToken).andExpect(status().isOk());

    for (String token : new String[] {PublicBookingFixtures.newToken(), verifyToken}) {
      f.manage(token)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("BOOKING_TOKEN_INVALID"))
          .andExpect(jsonPath("$.instance").value("/api/public/booking"));
      f.cancel(token)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("BOOKING_TOKEN_INVALID"));
    }
  }

  // ---- manage/cancel

  @Test
  void visitorCancellationCancelsClosesPublishesAndWarnsThePractitionerOnce() throws Exception {
    Practitioner p = f.practitioner("auto");
    Instant startsAt = slot(5, "09:00");
    String token = f.verifiedRequest(p, startsAt, applicantEmail());
    UUID appointment = appointmentOf(token);

    f.cancel(token)
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.cancelledBy").value("visitor"))
        .andExpect(jsonPath("$.canCancel").value(false))
        .andExpect(jsonPath("$.manageToken").isEmpty());

    Map<String, Object> row = f.appointmentRow(appointment);
    assertThat(row.get("status")).isEqualTo("cancelled");
    assertThat(row.get("cancelled_at")).isNotNull();
    assertThat(row.get("cancel_reason")).isEqualTo("Annulé par le demandeur");
    assertThat(closedBy(token)).isEqualTo("visitor");
    assertThat(f.publications(AppointmentCancelled.class, appointment)).isEqualTo(1);
    String text = mails.awaitText(p.email(), CANCELLED_SUBJECT);
    assertThat(text).isNotNull().contains("Sara Benali").contains("/agenda");

    // Second appel : même état, ni événement ni e-mail de plus.
    f.cancel(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"))
        .andExpect(jsonPath("$.cancelledBy").value("visitor"));
    assertThat(f.publications(AppointmentCancelled.class, appointment)).isEqualTo(1);
    assertThat(mails.countAfterQuiet(p.email(), CANCELLED_SUBJECT)).isEqualTo(1);
  }

  @Test
  void startedOrDoneAppointmentIs409AppointmentNotActive() throws Exception {
    Practitioner p = f.practitioner("auto");
    String started = f.verifiedRequest(p, slot(3, "09:00"), applicantEmail());
    Instant now = Instant.now();
    move(
        appointmentOf(started),
        now.minus(10, ChronoUnit.MINUTES),
        now.plus(50, ChronoUnit.MINUTES));

    f.manage(started).andExpect(jsonPath("$.canCancel").value(false));
    f.cancel(started)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_ACTIVE"))
        .andExpect(jsonPath("$.instance").value("/api/public/booking"));

    String past = f.verifiedRequest(p, slot(4, "09:00"), applicantEmail());
    Instant start = now.minus(3, ChronoUnit.DAYS);
    move(appointmentOf(past), start, start.plus(60, ChronoUnit.MINUTES));
    f.cancel(past).andExpect(status().isConflict());

    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment where practitioner_id = ? and status ="
                    + " 'cancelled'",
                Integer.class,
                p.id()))
        .isZero();
    assertThat(mails.countAfterQuiet(p.email(), CANCELLED_SUBJECT)).isZero();
  }
}
