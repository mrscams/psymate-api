package com.psymate.agenda;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.psymate.agenda.api.AppointmentCancelled;
import com.psymate.agenda.api.AppointmentPlanned;
import com.psymate.agenda.service.BookingMailer;
import com.psymate.identity.api.CurrentPractitioner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outils communs des IT de la prise de rendez-vous en ligne (M14-06) : praticiens offerts posés en
 * SQL, appels publics depuis une IP neuve, demandes posées en SQL avec un jeton connu, appels du
 * praticien avec une session de test. Aucune base simulée : Testcontainers seulement.
 */
final class PublicBookingFixtures {

  static final JsonMapper JSON = JsonMapper.builder().build();
  static final ZoneId PARIS = ZoneId.of("Europe/Paris");
  static final String BASE = "/api/public/booking";

  private static final AtomicInteger IP = new AtomicInteger();

  /** Lundi à dimanche, 09:00–12:00 : trois créneaux de 60 min par jour. */
  private static final String EVERY_MORNING =
      "[{\"day\":1,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":2,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":3,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":4,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":5,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":6,\"start\":\"09:00\",\"end\":\"12:00\"},"
          + "{\"day\":7,\"start\":\"09:00\",\"end\":\"12:00\"}]";

  /**
   * Abonnés de test : le registre Modulith n'écrit une publication dans {@code event_publication}
   * que s'il existe au moins un abonné ; l'agenda n'en a aucun en V1 (G Q59).
   */
  static class BookingEventsListener {
    @ApplicationModuleListener
    void on(AppointmentPlanned event) {}

    @ApplicationModuleListener
    void on(AppointmentCancelled event) {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ListenerConfig {
    @Bean
    BookingEventsListener publicBookingEventsListener() {
      return new BookingEventsListener();
    }
  }

  record Practitioner(UUID id, String slug, String displayName, String email) {}

  private final MockMvc mvc;
  private final JdbcTemplate jdbc;
  final UUID tenant;

  PublicBookingFixtures(MockMvc mvc, JdbcTemplate jdbc) {
    this.mvc = mvc;
    this.jdbc = jdbc;
    this.tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet réservation')", tenant);
  }

  // ---- praticiens

  /** Préférences d'un praticien dont la prise de rendez-vous est offerte. */
  static Map<String, Object> offered(String confirmation) {
    Map<String, Object> prefs = new LinkedHashMap<>();
    prefs.put("booking.enabled", true);
    prefs.put("booking.confirmation", confirmation);
    prefs.put("booking.windows", JSON.readValue(EVERY_MORNING, List.class));
    prefs.put("booking.slotDurationMin", 60);
    prefs.put("booking.minNoticeHours", 1);
    prefs.put("booking.horizonDays", 90);
    prefs.put("booking.modalities", List.of("video", "in_person"));
    prefs.put("booking.timezone", "Europe/Paris");
    return prefs;
  }

  Practitioner practitioner(String confirmation) {
    return practitioner(offered(confirmation));
  }

  Practitioner practitioner(Map<String, Object> prefs) {
    UUID id = UUID.randomUUID();
    String tag = id.toString().substring(0, 8);
    String email = "praticien-" + tag + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status,"
            + " preferences) values (?, ?, ?, ?, 'x', 'Claire', 'Lambert', 'Claire Lambert',"
            + " 'psychologist', 'approved', ?::jsonb)",
        id,
        tenant,
        email,
        email,
        JSON.writeValueAsString(prefs));
    String slug = "rdv-" + tag;
    String displayName = "Claire Lambert " + tag;
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, display_name, published)"
            + " values (?, ?, ?, true)",
        id,
        slug,
        displayName);
    return new Practitioner(id, slug, displayName, email);
  }

  /** Remplace une préférence du praticien (JSONB). */
  void setPreference(UUID practitioner, String key, Object value) {
    jdbc.update(
        "update practitioner set preferences = preferences || jsonb_build_object(?, ?::jsonb)"
            + " where id = ?",
        key,
        JSON.writeValueAsString(value),
        practitioner);
  }

  // ---- créneaux

  /** Instant d'une heure locale de Paris, {@code days} jours après aujourd'hui. */
  static Instant slot(int days, String hhmm) {
    return LocalDate.now(PARIS)
        .plusDays(days)
        .atTime(LocalTime.parse(hhmm))
        .atZone(PARIS)
        .toInstant();
  }

  /** Adresse unique d'un demandeur. */
  static String applicantEmail() {
    return "demandeur-" + UUID.randomUUID().toString().substring(0, 12) + "@example.test";
  }

  // ---- appels publics

  /** Chaque appel public vient d'une IP neuve : aucun seau de débit partagé entre tests. */
  static String freshIp() {
    int n = IP.incrementAndGet();
    return "10.66." + (n / 250) + "." + (n % 250 + 1);
  }

  /** Corps d'une demande bien formée, formulaire affiché il y a 30 s. */
  static Map<String, Object> request(Instant startsAt, String email) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("startsAt", startsAt.toString());
    m.put("modality", "video");
    m.put("firstName", "Sara");
    m.put("lastName", "Benali");
    m.put("email", email);
    m.put("phone", "+33 6 12 34 56 78");
    m.put("locale", "fr");
    m.put("consent", true);
    m.put("website", "");
    m.put("formShownAt", Instant.now().minusSeconds(30).toString());
    return m;
  }

  ResultActions postRequest(String slug, Map<String, Object> body) throws Exception {
    return postRequest(slug, body, freshIp());
  }

  ResultActions postRequest(String slug, Map<String, Object> body, String ip) throws Exception {
    return publicPost(BASE + "/practitioners/" + slug + "/requests", body, ip);
  }

  ResultActions verify(String token) throws Exception {
    return publicPost(BASE + "/requests/verify", Map.of("token", token), freshIp());
  }

  ResultActions manage(String token) throws Exception {
    return publicPost(BASE + "/manage", Map.of("token", token), freshIp());
  }

  ResultActions cancel(String token) throws Exception {
    return publicPost(BASE + "/manage/cancel", Map.of("token", token), freshIp());
  }

  ResultActions publicPost(String url, Object body, String ip) throws Exception {
    return mvc.perform(
        from(ip, MockMvcRequestBuilders.post(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8)));
  }

  static MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder b) {
    return b.with(
        r -> {
          r.setRemoteAddr(ip);
          return r;
        });
  }

  // ---- demandes posées en SQL

  /** Jeton aléatoire de 256 bits, comme l'API. */
  static String newToken() {
    byte[] raw = new byte[32];
    ThreadLocalRandom.current().nextBytes(raw);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  static String sha256(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Demande non vérifiée posée en SQL pour un créneau de 60 min, lien valable 30 min ; renvoie le
   * jeton de vérification en clair.
   */
  String pendingRequest(Practitioner p, Instant startsAt, String email) throws Exception {
    return pendingRequest(p, startsAt, email, Instant.now().plus(30, ChronoUnit.MINUTES));
  }

  String pendingRequest(Practitioner p, Instant startsAt, String email, Instant expiresAt)
      throws Exception {
    String token = newToken();
    jdbc.update(
        "insert into booking_request (id, tenant_id, practitioner_id, starts_at, ends_at,"
            + " modality, first_name, last_name, email, phone, locale, email_hash,"
            + " verify_token_hash, verify_expires_at) values (?, ?, ?, ?, ?, 'video', 'Sara',"
            + " 'Benali', ?, null, 'fr', ?, ?, ?)",
        UUID.randomUUID(),
        tenant,
        p.id(),
        Timestamp.from(startsAt),
        Timestamp.from(startsAt.plus(60, ChronoUnit.MINUTES)),
        email,
        "test-" + UUID.randomUUID(),
        sha256(token),
        Timestamp.from(expiresAt));
    return token;
  }

  /** Demande posée en SQL puis vérifiée par l'API ; renvoie le jeton de gestion. */
  String verifiedRequest(Practitioner p, Instant startsAt, String email) throws Exception {
    String token = pendingRequest(p, startsAt, email);
    MvcResult result = verify(token).andReturn();
    if (result.getResponse().getStatus() != 200) {
      throw new IllegalStateException(
          "verify " + result.getResponse().getStatus() + " " + body(result));
    }
    return json(result).path("manageToken").asString();
  }

  // ---- lectures

  static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  static JsonNode json(MvcResult result) throws Exception {
    return JSON.readTree(body(result));
  }

  Map<String, Object> requestRow(String verifyToken) throws Exception {
    return jdbc.queryForMap(
        "select * from booking_request where verify_token_hash = ?", sha256(verifyToken));
  }

  Map<String, Object> appointmentRow(Object appointmentId) {
    return jdbc.queryForMap("select * from appointment where id = ?", appointmentId);
  }

  int appointmentsOf(UUID practitionerId) {
    return jdbc.queryForObject(
        "select count(*) from appointment where practitioner_id = ?",
        Integer.class,
        practitionerId);
  }

  int requestsOf(UUID practitionerId) {
    return jdbc.queryForObject(
        "select count(*) from booking_request where practitioner_id = ?",
        Integer.class,
        practitionerId);
  }

  /** Publications du registre pour l'abonné de test, pour un type et un rendez-vous. */
  int publications(Class<?> eventType, Object appointmentId) {
    return jdbc.queryForObject(
        "select count(*) from event_publication where listener_id like '%BookingEventsListener%'"
            + " and event_type = ? and serialized_event::text like ?",
        Integer.class, eventType.getName(), "%" + appointmentId + "%");
  }

  // ---- praticien connecté

  RequestPostProcessor as(Practitioner p) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            p.id(), tenant, p.email(), "Claire Lambert", "psychologist", "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  ResultActions practitionerGet(Practitioner p, String url) throws Exception {
    return mvc.perform(MockMvcRequestBuilders.get(url).with(as(p)));
  }

  ResultActions practitionerPost(Practitioner p, String url, String body) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post(url)
            .with(as(p))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.getBytes(StandardCharsets.UTF_8)));
  }

  ResultActions practitionerPatch(Practitioner p, String url, String body) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.patch(url)
            .with(as(p))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.getBytes(StandardCharsets.UTF_8)));
  }

  // ---- M14-07 : suivi

  private static final Pattern MANAGE_LINK = Pattern.compile("/rendez-vous/gerer/([A-Za-z0-9_-]+)");

  /** Jeton du lien de gestion cité dans le texte d'un e-mail ; null s'il n'y en a pas. */
  static String manageTokenIn(String mailText) {
    Matcher m = MANAGE_LINK.matcher(mailText);
    return m.find() ? m.group(1) : null;
  }

  /** Rendez-vous d'un lien de gestion en cours. */
  UUID appointmentOf(String manageToken) throws Exception {
    return jdbc.queryForObject(
        "select appointment_id from booking_request where manage_token_hash = ?",
        UUID.class,
        sha256(manageToken));
  }

  /** Statut et clôture d'un rendez-vous en ligne et de sa demande. */
  Map<String, Object> state(UUID appointmentId) {
    return jdbc.queryForMap(
        "select a.status, a.cancel_reason, a.cancelled_at, b.closed_by, b.manage_token_hash"
            + " from appointment a left join booking_request b on b.appointment_id = a.id"
            + " where a.id = ?",
        appointmentId);
  }

  /**
   * Écoule ce qui précède un comptage d'e-mails (M14-07) : publications de ce rendez-vous pour
   * l'abonné de test terminées (30 s au plus, sans échec), puis file d'envoi des e-mails vide.
   */
  void settle(UUID appointmentId, BookingMailer mailer) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline
        && jdbc.queryForObject(
                "select count(*) from event_publication where completion_date is null"
                    + " and listener_id like '%BookingEventsListener%'"
                    + " and serialized_event::text like ?",
                Integer.class, "%" + appointmentId + "%")
            > 0) {
      Thread.sleep(50);
    }
    if (!mailer.awaitIdle(Duration.ofSeconds(30))) {
      throw new IllegalStateException("booking mail queue still busy after 30 s");
    }
  }
}
