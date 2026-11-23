package com.psymate.agenda;

import static com.psymate.agenda.PublicBookingFixtures.applicantEmail;
import static com.psymate.agenda.PublicBookingFixtures.freshIp;
import static com.psymate.agenda.PublicBookingFixtures.request;
import static com.psymate.agenda.PublicBookingFixtures.sha256;
import static com.psymate.agenda.PublicBookingFixtures.slot;
import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertArabicGetsFrench;
import static com.psymate.testsupport.ArabicFallback.frenchFallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.agenda.PublicBookingFixtures.Practitioner;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * M16-02 (G Q96, Q97, Q100) : prise de rendez-vous en ligne en arabe. Le champ {@code locale}
 * accepte {@code ar} et {@code booking_request.locale} le garde (V16) ; toute autre valeur reste
 * refusée. Les e-mails gardent leur source de langue ({@code booking_request.locale} pour le
 * demandeur, préférence {@code locale} pour le praticien) et partent en français tant que l'arabe
 * n'est pas traduit. Même configuration que PublicBookingVerifyIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, SharedMailpit.class, PublicBookingFixtures.ListenerConfig.class})
class BookingArabicIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private PublicBookingFixtures f;
  private PublicBookingMails mails;

  @BeforeEach
  void setUp() {
    f = new PublicBookingFixtures(mvc, jdbc);
    mails = new PublicBookingMails();
  }

  private ResultActions postRequest(String slug, Map<String, Object> body, String language)
      throws Exception {
    return mvc.perform(
        PublicBookingFixtures.from(
                freshIp(),
                MockMvcRequestBuilders.post(
                    PublicBookingFixtures.BASE + "/practitioners/" + slug + "/requests"))
            .header("Accept-Language", language)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                PublicBookingFixtures.JSON
                    .writeValueAsString(body)
                    .getBytes(StandardCharsets.UTF_8)));
  }

  /** Praticien offert dont la préférence {@code locale} vaut {@code ar}. */
  private Practitioner arabicPractitioner(String confirmation) {
    Map<String, Object> prefs = PublicBookingFixtures.offered(confirmation);
    prefs.put("locale", "ar");
    return f.practitioner(prefs);
  }

  @Test
  void localeArIsAcceptedStoredAndTheVerificationEmailIsSent() throws Exception {
    Practitioner p = arabicPractitioner("manual");
    String email = applicantEmail();
    Map<String, Object> body = request(slot(3, "09:00"), email);
    body.put("locale", "ar");

    postRequest(p.slug(), body, ARABIC).andExpect(status().isAccepted());

    assertThat(
            jdbc.queryForObject(
                "select locale from booking_request where practitioner_id = ?",
                String.class,
                p.id()))
        .isEqualTo("ar");
    if (frenchFallback()) {
      assertThat(mails.awaitText(email, "Confirmez votre demande de rendez-vous"))
          .isNotNull()
          .contains("PsyMate n'est pas un service d'urgence.");
    }
  }

  @Test
  void otherLocalesAreStillRefusedWithTheFrenchMessageInArabic() throws Exception {
    Practitioner p = f.practitioner("manual");
    for (String value : new String[] {"de", "AR", "ar-MA", "arabic"}) {
      Map<String, Object> body = request(slot(3, "09:00"), applicantEmail());
      body.put("locale", value);
      postRequest(p.slug(), body, "fr")
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.errors[0].field").value("locale"))
          .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    }
    Map<String, Object> body = request(slot(3, "09:00"), applicantEmail());
    body.put("locale", "de");
    assertArabicGetsFrench(postRequest(p.slug(), body, "fr"), postRequest(p.slug(), body, ARABIC));
    assertThat(f.requestsOf(p.id())).isZero();
  }

  /** Demande en {@code ar} vérifiée chez un praticien en {@code ar} : les deux e-mails partent. */
  @Test
  void verifiedArabicRequestSendsBothEmails() throws Exception {
    Practitioner p = arabicPractitioner("auto");
    String email = applicantEmail();
    Instant startsAt = slot(4, "10:00");
    String token = PublicBookingFixtures.newToken();
    UUID tenant =
        jdbc.queryForObject("select tenant_id from practitioner where id = ?", UUID.class, p.id());
    jdbc.update(
        "insert into booking_request (id, tenant_id, practitioner_id, starts_at, ends_at,"
            + " modality, first_name, last_name, email, phone, locale, email_hash,"
            + " verify_token_hash, verify_expires_at) values (?, ?, ?, ?, ?, 'video', 'Sara',"
            + " 'Benali', ?, null, 'ar', ?, ?, ?)",
        UUID.randomUUID(),
        tenant,
        p.id(),
        Timestamp.from(startsAt),
        Timestamp.from(startsAt.plus(60, ChronoUnit.MINUTES)),
        email,
        "test-" + UUID.randomUUID(),
        sha256(token),
        Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)));

    f.verify(token).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("confirmed"));

    if (frenchFallback()) {
      assertThat(mails.awaitText(email, "Votre rendez-vous")).isNotNull().contains("est confirmé");
      assertThat(mails.awaitText(p.email(), "Nouveau rendez-vous pris en ligne"))
          .isNotNull()
          .contains("Sara Benali");
    }
  }

  /** Erreurs des routes du praticien (AgendaMessages). */
  @Test
  void agendaErrorsInArabicGetTheFrenchMessages() throws Exception {
    Practitioner p = f.practitioner("manual");
    String url = "/api/agenda?from=2030-03-11T00:00:00Z&to=2030-03-11T00:00:00Z";
    assertArabicGetsFrench(
        mvc.perform(MockMvcRequestBuilders.get(url).header("Accept-Language", "fr").with(f.as(p)))
            .andExpect(status().isUnprocessableContent()),
        mvc.perform(
            MockMvcRequestBuilders.get(url).header("Accept-Language", ARABIC).with(f.as(p))));
    for (String create :
        new String[] {
          "{\"guest\":{\"lastName\":\"Benali\"},\"startsAt\":\"2030-03-11T09:00:00Z\","
              + "\"durationMin\":3,\"modality\":\"phone\"}",
          "{\"guest\":{\"lastName\":\"Benali\"},\"startsAt\":\"2030-03-11T09:00:00Z\","
              + "\"durationMin\":50,\"modality\":\"phone\",\"origin\":\"online\"}"
        }) {
      assertArabicGetsFrench(
          mvc.perform(
                  MockMvcRequestBuilders.post("/api/appointments")
                      .header("Accept-Language", "fr")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(create)
                      .with(f.as(p)))
              .andExpect(status().is4xxClientError()),
          mvc.perform(
              MockMvcRequestBuilders.post("/api/appointments")
                  .header("Accept-Language", ARABIC)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(create)
                  .with(f.as(p))));
    }
  }
}
