package com.psymate.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M14-05 (G Q71, Q75, Q76, Q78, K5.1) sur une vraie base (Testcontainers pgvector/pgvector:pg16) :
 * {@code GET /api/public/booking/practitioners/{slug}} et {@code …/slots}, sans session ni cookie.
 * Praticiens, fiches, préférences et rendez-vous sont posés en SQL ; aucune route publique n'écrit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class PublicBookingSlotsIT {

  private static final AtomicInteger IP = new AtomicInteger();
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
  private static final String BASE = "/api/public/booking/practitioners/";

  /** Lundi à dimanche, 09:00–12:00 : trois créneaux de 60 min par jour. */
  private static final String EVERY_MORNING =
      "[{\"day\":1,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":2,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":3,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":4,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":5,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":6,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":7,\"start\":\"09:00\",\"end\":\"12:00\"}]";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet réservation')", tenant);
  }

  /** Chaque appel vient d'une IP distincte pour ne pas partager le seau de 60 / min. */
  private static String freshIp() {
    int n = IP.incrementAndGet();
    return "10.88." + (n / 250) + "." + (n % 250 + 1);
  }

  private static MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder b) {
    return b.with(
        r -> {
          r.setRemoteAddr(ip);
          return r;
        });
  }

  private ResultActions anon(String url) throws Exception {
    return mvc.perform(from(freshIp(), get(url)));
  }

  private record Practitioner(UUID id, String slug) {}

  /** Préférences d'un praticien dont la prise de rendez-vous est offerte. */
  private static Map<String, Object> offered() {
    Map<String, Object> prefs = new LinkedHashMap<>();
    prefs.put("booking.enabled", true);
    prefs.put("booking.confirmation", "auto");
    prefs.put("booking.windows", JSON.readValue(EVERY_MORNING, List.class));
    prefs.put("booking.slotDurationMin", 60);
    prefs.put("booking.minNoticeHours", 1);
    prefs.put("booking.horizonDays", 90);
    prefs.put("booking.modalities", List.of("video", "in_person"));
    prefs.put("booking.timezone", "Europe/Paris");
    return prefs;
  }

  private Practitioner practitioner(String status, boolean published, Map<String, Object> prefs) {
    UUID id = UUID.randomUUID();
    String tag = id.toString().substring(0, 8);
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status,"
            + " preferences) values (?, ?, ?, ?, 'x', 'Claire', 'Lambert', 'Claire Lambert',"
            + " 'psychologist', ?, ?::jsonb)",
        id,
        tenant,
        email,
        email,
        status,
        JSON.writeValueAsString(prefs));
    String slug = "rdv-" + tag;
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, display_name, published)"
            + " values (?, ?, ?, ?)",
        id,
        slug,
        "Claire Lambert " + tag,
        published);
    return new Practitioner(id, slug);
  }

  private Practitioner offeredPractitioner() {
    return practitioner("approved", true, offered());
  }

  /** Rendez-vous invité, sans patient, posé en SQL. */
  private void appointment(UUID practitioner, Instant start, int minutes, String status) {
    jdbc.update(
        "insert into appointment (id, tenant_id, practitioner_id, starts_at, ends_at, modality,"
            + " status, cancelled_at, guest_last_name) values (?, ?, ?, ?, ?, 'video', ?, ?,"
            + " 'Benali')",
        UUID.randomUUID(),
        tenant,
        practitioner,
        Timestamp.from(start),
        Timestamp.from(start.plusSeconds(minutes * 60L)),
        status,
        "cancelled".equals(status) ? Timestamp.from(Instant.now()) : null);
  }

  private static Instant paris(LocalDate day, String hhmm) {
    return day.atTime(LocalTime.parse(hhmm)).atZone(PARIS).toInstant();
  }

  private static LocalDate today() {
    return LocalDate.now(PARIS);
  }

  private static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private static Set<String> fieldNames(JsonNode node) {
    return new HashSet<>(node.propertyNames());
  }

  private static List<Instant> starts(JsonNode slots) {
    List<Instant> starts = new ArrayList<>();
    for (JsonNode slot : slots) {
      starts.add(Instant.parse(slot.path("startsAt").asString()));
    }
    return starts;
  }

  @Test
  void settingsOfAnOfferedPractitionerArePublicAndUncached() throws Exception {
    Practitioner p = offeredPractitioner();

    MvcResult result =
        anon(BASE + p.slug())
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.slug").value(p.slug()))
            .andExpect(jsonPath("$.displayName").value(org.hamcrest.Matchers.startsWith("Claire")))
            .andExpect(jsonPath("$.confirmation").value("auto"))
            .andExpect(jsonPath("$.slotDurationMin").value(60))
            .andExpect(jsonPath("$.modalities[0]").value("in_person"))
            .andExpect(jsonPath("$.modalities[1]").value("video"))
            .andExpect(jsonPath("$.modalities.length()").value(2))
            .andExpect(jsonPath("$.timezone").value("Europe/Paris"))
            .andExpect(jsonPath("$.minNoticeHours").value(1))
            .andExpect(jsonPath("$.horizonDays").value(90))
            .andReturn();

    assertThat(fieldNames(JSON.readTree(body(result))))
        .containsExactlyInAnyOrder(
            "slug",
            "displayName",
            "confirmation",
            "slotDurationMin",
            "modalities",
            "timezone",
            "minNoticeHours",
            "horizonDays");
    assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @Test
  void everyUnavailableCaseGivesTheSame404OnBothRoutes() throws Exception {
    Map<String, Object> disabled = offered();
    disabled.put("booking.enabled", false);
    Map<String, Object> noWindow = offered();
    noWindow.put("booking.windows", List.of());
    Map<String, String> slugs = new LinkedHashMap<>();
    slugs.put("désactivée", practitioner("approved", true, disabled).slug());
    slugs.put("aucune plage", practitioner("approved", true, noWindow).slug());
    slugs.put("fiche non publiée", practitioner("approved", false, offered()).slug());
    slugs.put("praticien suspendu", practitioner("suspended", true, offered()).slug());
    slugs.put("slug inconnu", "inconnu-" + UUID.randomUUID());
    // Témoin : le même réglage, offert, répond 200.
    anon(BASE + offeredPractitioner().slug()).andExpect(status().isOk());

    List<String> bodies = new ArrayList<>();
    for (Map.Entry<String, String> e : slugs.entrySet()) {
      for (String url :
          List.of(
              BASE + e.getValue(),
              BASE + e.getValue() + "/slots",
              BASE + e.getValue() + "/slots?from=" + today().plusDays(1))) {
        MvcResult r =
            anon(url)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_AVAILABLE"))
                .andReturn();
        assertThat(r.getResponse().getHeader("Set-Cookie")).as(e.getKey()).isNull();
        String b = body(r);
        assertThat(b).as(e.getKey()).doesNotContain(e.getValue());
        bodies.add(b);
      }
    }
    assertThat(new HashSet<>(bodies)).as("corps des 404").hasSize(1);
    JsonNode problem = JSON.readTree(bodies.getFirst());
    assertThat(problem.path("status").asInt()).isEqualTo(404);
    assertThat(problem.path("instance").asString()).isEqualTo("/api/public/booking");
    assertThat(problem.path("detail").asString())
        .isEqualTo("Prise de rendez-vous en ligne indisponible à cette adresse.");
  }

  @Test
  void slotsExposeOnlyStartAndEndAndNeverAnActiveAppointment() throws Exception {
    Practitioner p = offeredPractitioner();
    Practitioner other = offeredPractitioner();
    LocalDate d = today().plusDays(7);
    appointment(p.id(), paris(d, "10:00"), 50, "planned");
    appointment(p.id(), paris(d.plusDays(1), "09:30"), 60, "tentative");
    appointment(p.id(), paris(d.plusDays(2), "11:00"), 60, "cancelled");
    appointment(p.id(), paris(d.plusDays(3), "09:00"), 60, "done");
    // Le rendez-vous d'un autre praticien ne retire rien.
    appointment(other.id(), paris(d, "09:00"), 60, "planned");

    MvcResult result =
        anon(BASE + p.slug() + "/slots?from=" + d + "&to=" + d.plusDays(6))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.timezone").value("Europe/Paris"))
            .andReturn();

    JsonNode root = JSON.readTree(body(result));
    assertThat(fieldNames(root)).containsExactlyInAnyOrder("timezone", "slots");
    JsonNode slots = root.path("slots");
    for (JsonNode slot : slots) {
      assertThat(fieldNames(slot)).containsExactlyInAnyOrder("startsAt", "endsAt");
      Instant s = Instant.parse(slot.path("startsAt").asString());
      Instant e = Instant.parse(slot.path("endsAt").asString());
      assertThat(e).isEqualTo(s.plusSeconds(3600));
      assertThat(slot.path("startsAt").asString()).endsWith("Z");
    }
    List<Instant> expected = new ArrayList<>();
    expected.add(paris(d, "09:00"));
    expected.add(paris(d, "11:00"));
    expected.add(paris(d.plusDays(1), "11:00"));
    for (int i = 2; i <= 6; i++) {
      for (String t : List.of("09:00", "10:00", "11:00")) {
        expected.add(paris(d.plusDays(i), t));
      }
    }
    assertThat(starts(slots)).containsExactlyElementsOf(expected);
    String raw = body(result);
    assertThat(raw).doesNotContain("Benali").doesNotContain(p.id().toString());
    assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @Test
  void defaultWindowIsTwoWeeksFromTodayInThePractitionerTimezone() throws Exception {
    Practitioner p = offeredPractitioner();

    JsonNode slots =
        JSON.readTree(body(anon(BASE + p.slug() + "/slots").andExpect(status().isOk()).andReturn()))
            .path("slots");

    List<LocalDate> days =
        starts(slots).stream()
            .map(s -> LocalDateTime.ofInstant(s, PARIS).toLocalDate())
            .distinct()
            .toList();
    LocalDate today = today();
    assertThat(days).allSatisfy(day -> assertThat(day).isBetween(today, today.plusDays(13)));
    assertThat(days).contains(today.plusDays(1), today.plusDays(13));

    LocalDate from = today.plusDays(20);
    JsonNode fromOnly =
        JSON.readTree(
                body(
                    anon(BASE + p.slug() + "/slots?from=" + from)
                        .andExpect(status().isOk())
                        .andReturn()))
            .path("slots");
    List<Instant> fromOnlyStarts = starts(fromOnly);
    assertThat(fromOnlyStarts).hasSize(14 * 3);
    assertThat(fromOnlyStarts.getFirst()).isEqualTo(paris(from, "09:00"));
    assertThat(fromOnlyStarts.getLast()).isEqualTo(paris(from.plusDays(13), "11:00"));
  }

  @Test
  void otherTimezoneAndDurationAreHonoured() throws Exception {
    Map<String, Object> prefs = offered();
    prefs.put("booking.timezone", "America/Montreal");
    prefs.put("booking.slotDurationMin", 45);
    prefs.put("booking.windows", List.of(Map.of("day", 1, "start", "09:00", "end", "12:00")));
    Practitioner p = practitioner("approved", true, prefs);
    ZoneId montreal = ZoneId.of("America/Montreal");
    LocalDate monday = LocalDate.now(montreal).plusWeeks(2).with(java.time.DayOfWeek.MONDAY);

    JsonNode root =
        JSON.readTree(
            body(
                anon(BASE + p.slug() + "/slots?from=" + monday + "&to=" + monday)
                    .andExpect(status().isOk())
                    .andReturn()));

    assertThat(root.path("timezone").asString()).isEqualTo("America/Montreal");
    assertThat(
            starts(root.path("slots")).stream()
                .map(s -> LocalDateTime.ofInstant(s, montreal).toLocalTime().toString())
                .toList())
        .containsExactly("09:00", "09:45", "10:30", "11:15");
  }

  @Test
  void windowOf32DaysIsTooLargeAnd31DaysIsAccepted() throws Exception {
    Practitioner p = offeredPractitioner();
    LocalDate from = today().plusDays(1);

    anon(BASE + p.slug() + "/slots?from=" + from + "&to=" + from.plusDays(31))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("WINDOW_TOO_LARGE"))
        .andExpect(jsonPath("$.errors[0].field").value("to"))
        .andExpect(jsonPath("$.errors[0].code").value("WINDOW_TOO_LARGE"))
        .andExpect(jsonPath("$.detail").value("La fenêtre demandée dépasse 31 jours."));

    anon(BASE + p.slug() + "/slots?from=" + from + "&to=" + from.plusDays(30))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots.length()").value(31 * 3));
  }

  @Test
  void toBeforeFromIsInvalidValueOnTo() throws Exception {
    Practitioner p = offeredPractitioner();
    LocalDate from = today().plusDays(5);

    anon(BASE + p.slug() + "/slots?from=" + from + "&to=" + from.minusDays(1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("to"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("La fin de la fenêtre (to) ne peut pas précéder son début (from)."));

    // to = from : un seul jour, accepté.
    anon(BASE + p.slug() + "/slots?from=" + from + "&to=" + from)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots.length()").value(3));
  }

  @Test
  void unreadableDateIsInvalidFormatAndStillUncached() throws Exception {
    Practitioner p = offeredPractitioner();

    anon(BASE + p.slug() + "/slots?from=07/12/2026")
        .andExpect(status().isUnprocessableContent())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("from"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
  }

  @Test
  void sixtyFirstRequestInAMinuteFromOneIpIsRateLimited() throws Exception {
    Practitioner p = offeredPractitioner();
    String ip = freshIp();
    for (int i = 0; i < 60; i++) {
      String url = i % 2 == 0 ? BASE + p.slug() : BASE + p.slug() + "/slots";
      mvc.perform(from(ip, get(url))).andExpect(status().isOk());
    }
    mvc.perform(from(ip, get(BASE + p.slug() + "/slots")))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(header().exists("Retry-After"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    // Une autre adresse n'est pas touchée.
    anon(BASE + p.slug()).andExpect(status().isOk());
  }

  @Test
  void sixthBookingRequestInAnHourFromOneIpIsRateLimited() throws Exception {
    // Règle public-booking (G Q79) déclarée ici pour M14-06 : 5 POST / h / IP sur …/requests.
    String ip = freshIp();
    String url = BASE + "absent/requests";
    for (int i = 0; i < 5; i++) {
      MockHttpServletResponse r =
          mvc.perform(from(ip, post(url)).contentType(MediaType.APPLICATION_JSON).content("{}"))
              .andReturn()
              .getResponse();
      assertThat(r.getStatus()).isNotEqualTo(429);
    }
    mvc.perform(from(ip, post(url)).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    // Les lectures gardent leur propre seau.
    mvc.perform(from(ip, get(BASE + "absent"))).andExpect(status().isNotFound());
  }

  @Test
  void publicRoutesWriteNothing() throws Exception {
    Practitioner p = offeredPractitioner();
    appointment(p.id(), paris(today().plusDays(3), "10:00"), 60, "planned");
    String sql = "select count(*) from %s where practitioner_id = ?";

    anon(BASE + p.slug()).andExpect(status().isOk());
    anon(BASE + p.slug() + "/slots").andExpect(status().isOk());

    assertThat(jdbc.queryForObject(sql.formatted("appointment"), Integer.class, p.id()))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject(sql.formatted("booking_request"), Integer.class, p.id()))
        .isZero();
  }
}
