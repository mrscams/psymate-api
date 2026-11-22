package com.psymate.agenda;

import static com.psymate.agenda.PublicBookingFixtures.applicantEmail;
import static com.psymate.agenda.PublicBookingFixtures.sha256;
import static com.psymate.agenda.PublicBookingFixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.agenda.PublicBookingFixtures.Practitioner;
import com.psymate.agenda.service.BookingPurgeJob;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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
 * Purge des demandes en ligne (M14-07, E9, G Q82 a et f) sur une vraie base (Testcontainers
 * pgvector/pgvector:pg16) : ligne {@code booking_request} supprimée 24 h après sa création si elle
 * n'a jamais été vérifiée, 30 jours après la fin ou l'annulation du rendez-vous sinon ; aucune
 * ligne {@code appointment} n'est jamais supprimée. Les délais passent par l'horloge injectée
 * ({@link BookingTestClock}).
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
class BookingPurgeJobIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BookingPurgeJob job;
  @Autowired private BookingTestClock.OffsetClock clock;

  private PublicBookingFixtures f;

  @BeforeEach
  void setUp() {
    clock.reset();
    f = new PublicBookingFixtures(mvc, jdbc);
  }

  @AfterEach
  void resetClock() {
    clock.reset();
  }

  private boolean unverifiedExists(String verifyToken) throws Exception {
    return jdbc.queryForObject(
            "select count(*) from booking_request where verify_token_hash = ?",
            Integer.class,
            sha256(verifyToken))
        > 0;
  }

  private boolean requestExists(UUID appointment) {
    return jdbc.queryForObject(
            "select count(*) from booking_request where appointment_id = ?",
            Integer.class,
            appointment)
        > 0;
  }

  private int allAppointments() {
    return jdbc.queryForObject("select count(*) from appointment", Integer.class);
  }

  private UUID cancelledByThePractitioner(Practitioner p, Instant startsAt) throws Exception {
    UUID id = f.appointmentOf(f.verifiedRequest(p, startsAt, applicantEmail()));
    f.practitionerPost(p, "/api/appointments/" + id + "/cancel", "{}").andExpect(status().isOk());
    return id;
  }

  @Test
  void defaultRetentionsAreTwentyFourHoursAndThirtyDays() {
    assertThat(job.unverifiedRetention()).isEqualTo(Duration.ofHours(24));
    assertThat(job.closedRetention()).isEqualTo(Duration.ofDays(30));
  }

  // ---- demande jamais vérifiée (Q82 a)

  @Test
  void unverifiedRequestOfTwentyFiveHoursIsDeleted() throws Exception {
    Practitioner p = f.practitioner("manual");
    String token = f.pendingRequest(p, slot(3, "09:00"), applicantEmail());

    clock.advance(Duration.ofHours(25));
    assertThat(job.run().unverified()).isGreaterThanOrEqualTo(1);

    assertThat(unverifiedExists(token)).isFalse();
  }

  @Test
  void unverifiedRequestOfTwentyThreeHoursIsKept() throws Exception {
    Practitioner p = f.practitioner("manual");
    String token = f.pendingRequest(p, slot(3, "10:00"), applicantEmail());

    clock.advance(Duration.ofHours(23));
    job.run();

    assertThat(unverifiedExists(token)).isTrue();
  }

  // ---- demande vérifiée (Q82 f)

  @Test
  void requestOfAnAppointmentCancelledThirtyOneDaysAgoIsDeletedAppointmentStays() throws Exception {
    Practitioner p = f.practitioner("manual");
    UUID id = cancelledByThePractitioner(p, slot(4, "09:00"));
    int before = allAppointments();

    clock.advance(Duration.ofDays(31));
    assertThat(job.run().closed()).isGreaterThanOrEqualTo(1);

    assertThat(requestExists(id)).isFalse();
    assertThat(f.appointmentRow(id).get("status")).isEqualTo("cancelled");
    assertThat(allAppointments()).isEqualTo(before);
  }

  @Test
  void requestOfAnAppointmentCancelledTwentyNineDaysAgoIsKept() throws Exception {
    Practitioner p = f.practitioner("manual");
    UUID id = cancelledByThePractitioner(p, slot(4, "10:00"));

    clock.advance(Duration.ofDays(29));
    job.run();

    assertThat(requestExists(id)).isTrue();
  }

  @Test
  void requestOfAnAppointmentEndedThirtyOneDaysAgoIsDeletedTwentyNineDaysKept() throws Exception {
    Practitioner p = f.practitioner("auto");
    Instant startsAt = slot(5, "09:00");
    UUID ended = f.appointmentOf(f.verifiedRequest(p, startsAt, applicantEmail()));
    Instant endsAt = startsAt.plus(Duration.ofMinutes(60));

    clock.advance(Duration.between(Instant.now(), endsAt).plus(Duration.ofDays(29)));
    job.run();
    assertThat(requestExists(ended)).isTrue();

    clock.advance(Duration.between(Instant.now(), endsAt).plus(Duration.ofDays(31)));
    job.run();
    assertThat(requestExists(ended)).isFalse();
    assertThat(f.appointmentRow(ended).get("status")).isEqualTo("planned");
  }

  @Test
  void requestOfAnUpcomingAppointmentIsKept() throws Exception {
    Practitioner p = f.practitioner("manual");
    UUID id = f.appointmentOf(f.verifiedRequest(p, slot(10, "09:00"), applicantEmail()));

    // Bien au-delà des 24 h d'une demande non vérifiée, avant la fin du rendez-vous.
    clock.advance(Duration.ofDays(2));
    job.run();

    assertThat(requestExists(id)).isTrue();
  }

  @Test
  void purgeNeverDeletesAnAppointment() throws Exception {
    Practitioner p = f.practitioner("manual");
    cancelledByThePractitioner(p, slot(4, "11:00"));
    f.pendingRequest(p, slot(5, "11:00"), applicantEmail());
    int before = allAppointments();

    clock.advance(Duration.ofDays(400));
    job.run();

    assertThat(allAppointments()).isEqualTo(before);
    assertThat(f.appointmentsOf(p.id())).isEqualTo(1);
    assertThat(f.requestsOf(p.id())).isZero();
  }
}
