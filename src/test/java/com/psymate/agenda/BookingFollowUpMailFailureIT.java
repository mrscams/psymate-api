package com.psymate.agenda;

import static com.psymate.agenda.PublicBookingFixtures.applicantEmail;
import static com.psymate.agenda.PublicBookingFixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.agenda.PublicBookingFixtures.Practitioner;
import com.psymate.agenda.service.BookingExpiryJob;
import com.psymate.agenda.service.BookingMailer;
import com.psymate.agenda.service.BookingPurgeJob;
import com.psymate.testsupport.SharedPostgres;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Échec du serveur d'envoi pour les e-mails de suivi (M14-07, G Q81) : SMTP injoignable (le même
 * que {@link PublicBookingMailFailureIT}), ni confirm, ni PATCH, ni cancel, ni les tâches
 * planifiées n'échouent. Pas de base simulée.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
  SharedPostgres.class,
  PublicBookingMailFailureIT.UnreachableSmtp.class,
  BookingTestClock.class
})
class BookingFollowUpMailFailureIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BookingMailer mailer;
  @Autowired private BookingExpiryJob expiry;
  @Autowired private BookingPurgeJob purge;
  @Autowired private BookingTestClock.OffsetClock clock;

  @AfterEach
  void resetClock() {
    clock.reset();
  }

  @Test
  void smtpFailureFailsNeitherThePractitionerRoutesNorTheJobs() throws Exception {
    clock.reset();
    PublicBookingFixtures f = new PublicBookingFixtures(mvc, jdbc);
    Practitioner p = f.practitioner("manual");

    UUID id = f.appointmentOf(f.verifiedRequest(p, slot(6, "09:00"), applicantEmail()));
    f.practitionerPost(p, "/api/appointments/" + id + "/confirm", "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("planned"));
    f.practitionerPatch(p, "/api/appointments/" + id, "{\"startsAt\":\"" + slot(6, "11:00") + "\"}")
        .andExpect(status().isOk());
    f.practitionerPost(p, "/api/appointments/" + id + "/cancel", "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"));

    UUID pending = f.appointmentOf(f.verifiedRequest(p, slot(7, "09:00"), applicantEmail()));
    // Laisse partir (et échouer) les envois précédents.
    assertThat(mailer.awaitIdle(Duration.ofSeconds(30))).isTrue();

    clock.advance(Duration.ofHours(73));
    assertThat(expiry.run()).isGreaterThanOrEqualTo(1);
    assertThat(f.state(pending).get("closed_by")).isEqualTo("expired");
    clock.advance(Duration.ofDays(40));
    purge.run();
    assertThat(mailer.awaitIdle(Duration.ofSeconds(30))).isTrue();
  }
}
