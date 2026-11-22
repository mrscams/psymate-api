package com.psymate.agenda;

import static com.psymate.agenda.PublicBookingFixtures.applicantEmail;
import static com.psymate.agenda.PublicBookingFixtures.body;
import static com.psymate.agenda.PublicBookingFixtures.freshIp;
import static com.psymate.agenda.PublicBookingFixtures.request;
import static com.psymate.agenda.PublicBookingFixtures.sha256;
import static com.psymate.agenda.PublicBookingFixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.agenda.PublicBookingFixtures.Practitioner;
import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code POST /api/public/booking/practitioners/{slug}/requests} (M14-06, G Q72, Q73, Q78, Q79) sur
 * une vraie base (Testcontainers pgvector/pgvector:pg16) et un vrai serveur d'envoi (Mailpit) :
 * demande enregistrée et lien de vérification envoyé, envois ignorés en silence, plafonds, créneau
 * ou modalité non offerts, validation, et réponse indépendante de l'adresse saisie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, SharedMailpit.class})
class PublicBookingRequestIT {

  private static final String VERIFY_SUBJECT = "Confirmez votre demande de rendez-vous";

  private static final Pattern VERIFY_LINK =
      Pattern.compile("/rendez-vous/confirmer/([A-Za-z0-9_-]+)");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private PublicBookingFixtures f;
  private PublicBookingMails mails;

  @BeforeEach
  void setUp() {
    f = new PublicBookingFixtures(mvc, jdbc);
    mails = new PublicBookingMails();
  }

  // ---- demande acceptée

  @Test
  void wellFormedRequestStoresAnUnverifiedRowAndSendsOnlyTheVerificationEmail() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    Instant startsAt = slot(3, "09:00");

    MvcResult result =
        f.postRequest(p.slug(), request(startsAt, email))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn();
    assertThat(body(result)).isEmpty();
    assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();

    Map<String, Object> row =
        jdbc.queryForMap("select * from booking_request where practitioner_id = ?", p.id());
    assertThat(row.get("verified_at")).isNull();
    assertThat(row.get("appointment_id")).isNull();
    assertThat(row.get("first_name")).isEqualTo("Sara");
    assertThat(row.get("last_name")).isEqualTo("Benali");
    assertThat(row.get("email")).isEqualTo(email);
    assertThat(row.get("phone")).isEqualTo("+33 6 12 34 56 78");
    assertThat(row.get("locale")).isEqualTo("fr");
    assertThat(row.get("modality")).isEqualTo("video");
    assertThat(((Timestamp) row.get("starts_at")).toInstant()).isEqualTo(startsAt);
    assertThat(((Timestamp) row.get("ends_at")).toInstant())
        .isEqualTo(startsAt.plus(60, ChronoUnit.MINUTES));
    assertThat((String) row.get("email_hash")).matches("[0-9a-f]{64}").doesNotContain("sara");
    Instant created = ((Timestamp) row.get("created_at")).toInstant();
    assertThat(((Timestamp) row.get("verify_expires_at")).toInstant())
        .isEqualTo(created.plus(30, ChronoUnit.MINUTES));
    assertThat(f.appointmentsOf(p.id())).isZero();

    String text = mails.awaitText(email, VERIFY_SUBJECT);
    assertThat(text).as("e-mail de vérification").isNotNull();
    Matcher link = VERIFY_LINK.matcher(text);
    assertThat(link.find()).isTrue();
    String token = link.group(1);
    assertThat(token).hasSize(43);
    // Le jeton n'est gardé que haché.
    assertThat(row.get("verify_token_hash")).isEqualTo(sha256(token));
    assertThat(row.values().stream().map(String::valueOf)).noneMatch(v -> v.contains(token));
    assertThat(text)
        .contains(p.displayName())
        .contains("(fuseau Europe/Paris)")
        .contains("Durée : 60 minutes")
        .contains("en visioconférence")
        .contains("30 minutes")
        .contains("PsyMate n'est pas un service d'urgence.");
    // Objet neutre (Q81) : le message a été trouvé par son objet exact, sans nom ni profession.
    assertThat(VERIFY_SUBJECT).doesNotContain("Lambert").doesNotContainIgnoringCase("psycholog");
    assertThat(mails.countAllAfterQuiet(email)).isEqualTo(1);
    // Rien chez le praticien avant la vérification.
    assertThat(mails.countAllAfterQuiet(p.email())).isZero();
  }

  @Test
  void englishLocaleGivesAnEnglishVerificationEmail() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    Map<String, Object> body = request(slot(3, "10:00"), email);
    body.put("locale", "en");
    body.remove("phone");

    f.postRequest(p.slug(), body).andExpect(status().isAccepted());

    String text = mails.awaitText(email, "Confirm your appointment request");
    assertThat(text).isNotNull().contains("PsyMate is not an emergency service.");
    assertThat(
            jdbc.queryForMap(
                "select locale, phone from booking_request where practitioner_id = ?", p.id()))
        .containsEntry("locale", "en")
        .containsEntry("phone", null);
  }

  // ---- ignorées en silence (Q78)

  @Test
  void honeypotTooFastOrFarFutureFormIsAcceptedWithoutRowNorEmail() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    Instant startsAt = slot(3, "09:00");

    Map<String, Object> honeypot = request(startsAt, email);
    honeypot.put("website", "https://spam.example.test");
    Map<String, Object> tooFast = request(startsAt, email);
    tooFast.put("formShownAt", Instant.now().minusSeconds(1).toString());
    // Au-delà de la tolérance de 5 minutes sur l'horloge du client : ignorée en silence.
    Map<String, Object> future = request(startsAt, email);
    future.put("formShownAt", Instant.now().plus(6, ChronoUnit.MINUTES).toString());

    for (Map<String, Object> body : List.of(honeypot, tooFast, future)) {
      MvcResult r = f.postRequest(p.slug(), body).andExpect(status().isAccepted()).andReturn();
      assertThat(body(r)).isEmpty();
    }
    assertThat(f.requestsOf(p.id())).isZero();
    assertThat(mails.countAllAfterQuiet(email)).isZero();
  }

  @Test
  void formShownFourMinutesInTheFutureIsAcceptedWithRowAndEmail() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    Map<String, Object> body = request(slot(3, "11:00"), email);
    // Horloge du client en avance de 4 minutes : dans la tolérance de 5 minutes.
    body.put("formShownAt", Instant.now().plus(4, ChronoUnit.MINUTES).toString());

    f.postRequest(p.slug(), body).andExpect(status().isAccepted());

    assertThat(f.requestsOf(p.id())).isEqualTo(1);
    assertThat(mails.awaitText(email, VERIFY_SUBJECT)).isNotNull();
  }

  @Test
  void formShownSixMinutesInTheFutureIsIgnored() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    Map<String, Object> body = request(slot(3, "11:00"), email);
    body.put("formShownAt", Instant.now().plus(6, ChronoUnit.MINUTES).toString());

    MvcResult r = f.postRequest(p.slug(), body).andExpect(status().isAccepted()).andReturn();

    assertThat(body(r)).isEmpty();
    assertThat(f.requestsOf(p.id())).isZero();
    assertThat(mails.countAllAfterQuiet(email)).isZero();
  }

  @Test
  void fourthRequestOfTheSameAddressWithinAnHourSendsNothing() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    // Même adresse, casse différente : même empreinte.
    List<String> spellings =
        List.of(
            email,
            email.toUpperCase(),
            email.replace("demandeur", "Demandeur"),
            email.replace("example", "EXAMPLE"));
    for (String spelling : spellings) {
      f.postRequest(p.slug(), request(slot(4, "09:00"), spelling)).andExpect(status().isAccepted());
    }

    assertThat(f.requestsOf(p.id())).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "select count(distinct email_hash) from booking_request where practitioner_id = ?",
                Integer.class,
                p.id()))
        .isEqualTo(1);
    assertThat(mails.awaitCount(email, VERIFY_SUBJECT, 3)).isEqualTo(3);
    assertThat(mails.countAfterQuiet(email, VERIFY_SUBJECT)).isEqualTo(3);
  }

  @Test
  void simultaneousRequestsOfTheSameAddressNeverExceedTheCap() throws Exception {
    Practitioner p = f.practitioner("manual");
    String email = applicantEmail();
    int threads = 8;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        // Une IP par fil : la règle public-booking (5 par heure et par IP) n'intervient pas.
        String ip = freshIp();
        Callable<Integer> call =
            () -> {
              start.await();
              return f.postRequest(p.slug(), request(slot(4, "10:00"), email), ip)
                  .andReturn()
                  .getResponse()
                  .getStatus();
            };
        results.add(pool.submit(call));
      }
      start.countDown();
      for (Future<Integer> r : results) {
        assertThat(r.get(60, TimeUnit.SECONDS)).isEqualTo(202);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(f.requestsOf(p.id())).isEqualTo(3);
    assertThat(mails.awaitCount(email, VERIFY_SUBJECT, 3)).isEqualTo(3);
    assertThat(mails.countAfterQuiet(email, VERIFY_SUBJECT)).isEqualTo(3);
  }

  @Test
  void sixthCreationFromTheSameIpWithinAnHourIs429() throws Exception {
    Practitioner p = f.practitioner("manual");
    String ip = freshIp();
    for (int i = 0; i < 5; i++) {
      Map<String, Object> body = request(slot(5, "09:00"), applicantEmail());
      body.put("website", "pot-de-miel");
      f.postRequest(p.slug(), body, ip).andExpect(status().isAccepted());
    }

    f.postRequest(p.slug(), request(slot(5, "09:00"), applicantEmail()), ip)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    assertThat(f.requestsOf(p.id())).isZero();
  }

  // ---- créneau ou modalité non offerts (409)

  @Test
  void startsAtThatIsNotAnOfferedSlotIs409SlotNotAvailable() throws Exception {
    Map<String, Object> prefs = PublicBookingFixtures.offered("manual");
    prefs.put("booking.minNoticeHours", 48);
    Practitioner p = f.practitioner(prefs);
    Instant taken = slot(6, "10:00");
    jdbc.update(
        "insert into appointment (id, tenant_id, practitioner_id, starts_at, ends_at, modality,"
            + " status, guest_last_name) values (?, ?, ?, ?, ?, 'video', 'planned', 'Autre')",
        UUID.randomUUID(),
        f.tenant,
        p.id(),
        Timestamp.from(taken),
        Timestamp.from(taken.plus(50, ChronoUnit.MINUTES)));

    List<Instant> notOffered =
        List.of(
            slot(6, "09:30"), // pas un début de créneau
            slot(6, "13:00"), // hors plage
            taken, // occupé
            slot(1, "09:00"), // dans moins de 48 h : délai minimal
            slot(120, "09:00")); // au-delà de l'horizon
    List<String> bodies = new ArrayList<>();
    for (Instant startsAt : notOffered) {
      MvcResult r =
          f.postRequest(p.slug(), request(startsAt, applicantEmail()))
              .andExpect(status().isConflict())
              .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
              .andExpect(header().string("Cache-Control", "no-store"))
              .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"))
              .andExpect(jsonPath("$.instance").value("/api/public/booking"))
              .andReturn();
      bodies.add(body(r));
    }
    assertThat(bodies).allMatch(b -> b.equals(bodies.get(0)));
    assertThat(f.requestsOf(p.id())).isZero();

    // Témoin : le créneau voisin est offert.
    f.postRequest(p.slug(), request(slot(6, "09:00"), applicantEmail()))
        .andExpect(status().isAccepted());
  }

  @Test
  void modalityNotOfferedIs409SlotNotAvailable() throws Exception {
    Practitioner p = f.practitioner("manual");
    Map<String, Object> body = request(slot(3, "09:00"), applicantEmail());
    body.put("modality", "phone");

    f.postRequest(p.slug(), body)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
    assertThat(f.requestsOf(p.id())).isZero();
  }

  // ---- validation (422) et 404

  @Test
  void malformedFieldsAre422WithACodePerField() throws Exception {
    Practitioner p = f.practitioner("manual");
    Instant startsAt = slot(3, "09:00");
    Map<String, String[]> cases = new LinkedHashMap<>();
    cases.put("consent", new String[] {"consent", "false", "INVALID_VALUE"});
    cases.put("email", new String[] {"email", "\"abc\"", "INVALID_FORMAT"});
    cases.put("firstName", new String[] {"firstName", "\"  \"", "REQUIRED"});
    cases.put("lastName", new String[] {"lastName", "\"" + "x".repeat(101) + "\"", "OUT_OF_RANGE"});
    cases.put("phone", new String[] {"phone", "\"abc\"", "INVALID_FORMAT"});
    cases.put("locale", new String[] {"locale", "\"de\"", "INVALID_VALUE"});
    cases.put("modality", new String[] {"modality", "\"sofa\"", "INVALID_VALUE"});
    cases.put("startsAt", new String[] {"startsAt", "\"demain\"", "INVALID_FORMAT"});
    cases.put("formShownAt", new String[] {"formShownAt", "null", "REQUIRED"});

    for (String[] c : cases.values()) {
      Map<String, Object> body = request(startsAt, applicantEmail());
      body.put(c[0], PublicBookingFixtures.JSON.readValue(c[1], Object.class));
      f.postRequest(p.slug(), body)
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.instance").value("/api/public/booking"))
          .andExpect(jsonPath("$.errors[0].field").value(c[0]))
          .andExpect(jsonPath("$.errors[0].code").value(c[2]))
          .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }
    assertThat(f.requestsOf(p.id())).isZero();
  }

  @Test
  void unknownSlugOrDisabledFeatureIs404BookingNotAvailableWithTheSameBody() throws Exception {
    Map<String, Object> disabledPrefs = PublicBookingFixtures.offered("manual");
    disabledPrefs.put("booking.enabled", false);
    Practitioner disabled = f.practitioner(disabledPrefs);
    Instant startsAt = slot(3, "09:00");

    String unknown =
        body(
            f.postRequest("inconnu-" + UUID.randomUUID(), request(startsAt, applicantEmail()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_AVAILABLE"))
                .andReturn());
    String off =
        body(
            f.postRequest(disabled.slug(), request(startsAt, applicantEmail()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn());
    assertThat(off).isEqualTo(unknown).doesNotContain(disabled.slug());
    assertThat(f.requestsOf(disabled.id())).isZero();
  }

  // ---- Q78 : la réponse ne dépend pas de l'adresse saisie

  @Test
  void responseDoesNotDependOnWhetherTheAddressAlreadyHasAnAppointment() throws Exception {
    Practitioner p = f.practitioner("manual");
    String known = applicantEmail();
    String unknown = applicantEmail();
    Instant existing = slot(8, "09:00");
    jdbc.update(
        "insert into appointment (id, tenant_id, practitioner_id, starts_at, ends_at, modality,"
            + " status, guest_last_name, guest_email, origin)"
            + " values (?, ?, ?, ?, ?, 'video', 'tentative', 'Benali', ?, 'online')",
        UUID.randomUUID(),
        f.tenant,
        p.id(),
        Timestamp.from(existing),
        Timestamp.from(existing.plus(60, ChronoUnit.MINUTES)),
        known);
    Instant startsAt = slot(9, "10:00");
    String formShownAt = Instant.now().minusSeconds(30).toString();

    Map<String, Object> first = request(startsAt, known);
    first.put("formShownAt", formShownAt);
    Map<String, Object> second = request(startsAt, unknown);
    second.put("formShownAt", formShownAt);
    MockHttpServletResponse a = f.postRequest(p.slug(), first).andReturn().getResponse();
    MockHttpServletResponse b = f.postRequest(p.slug(), second).andReturn().getResponse();

    assertThat(a.getStatus()).isEqualTo(202).isEqualTo(b.getStatus());
    assertThat(a.getContentAsString()).isEqualTo(b.getContentAsString()).isEmpty();
    assertThat(headers(a)).isEqualTo(headers(b));
  }

  // ---- outils

  /** En-têtes d'une réponse, hors Date. */
  private static Map<String, List<String>> headers(MockHttpServletResponse response) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (String name : response.getHeaderNames()) {
      if (!name.equalsIgnoreCase("Date")) {
        headers.put(name.toLowerCase(), response.getHeaders(name));
      }
    }
    return headers;
  }
}
