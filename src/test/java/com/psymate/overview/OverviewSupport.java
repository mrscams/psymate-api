package com.psymate.overview;

import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.psymate.PostgresTestConfig;
import com.psymate.identity.api.CurrentPractitioner;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Socle des tests de la vue d'ensemble : Postgres pgvector (Testcontainers), un cabinet, un
 * praticien, un patient et une séance ouverte par test ; les entrées sont créées par l'API de
 * clinical (comme en production) ou, pour les cas que l'API ne sait pas exprimer (écriture sans
 * événement, volumétrie), par SQL. L'horloge est fixée ({@link OverviewTestConfig}). Jamais de mock
 * de la base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, OverviewTestConfig.class})
public abstract class OverviewSupport {

  /** Instant courant fixé par {@link OverviewTestConfig}. */
  protected static final Instant NOW = OverviewTestConfig.NOW;

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected TransactionTemplate tx;
  @Autowired protected OverviewTestConfig.MutableClock clock;

  protected UUID tenant;
  protected UUID me;
  protected UUID patient;
  protected UUID session;
  protected RequestPostProcessor auth;

  @BeforeEach
  void baseSetUp() throws Exception {
    SecurityContextHolder.clearContext();
    clock.set(NOW);
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    me = newPractitioner();
    patient = newPatient(me);
    auth = as(me);
    session = UUID.fromString(startSession());
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  // ---- données de base

  protected UUID newPractitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  protected UUID newPatient(UUID owner) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name, birth_date)"
            + " values (?, ?, ?, 'Patient test', date '1990-04-02')",
        id,
        tenant,
        owner);
    return id;
  }

  protected RequestPostProcessor as(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            tenant,
            practitionerId + "@example.test",
            "Dr Test",
            "psychologist",
            "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  // ---- requêtes HTTP

  protected static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  protected ResultActions send(MockHttpServletRequestBuilder builder, Object json)
      throws Exception {
    if (json != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(JSON.writeValueAsString(json).getBytes(StandardCharsets.UTF_8));
    }
    return mvc.perform(builder.with(auth));
  }

  protected String id(ResultActions result) throws Exception {
    return JsonPath.read(body(result), "$.id");
  }

  protected String startSession() throws Exception {
    return id(
        send(
                MockMvcRequestBuilders.post("/api/patients/{id}/sessions/start", patient),
                Map.of("modality", "in_person"))
            .andExpect(status().isCreated()));
  }

  /** Clôture la séance ouverte puis en ouvre une autre ; renvoie l'id de la nouvelle. */
  protected UUID nextSession() throws Exception {
    send(
            MockMvcRequestBuilders.post("/api/sessions/{id}/close", session),
            Map.of("durationMin", 50, "summary", "Fin de séance"))
        .andExpect(status().isOk());
    session = UUID.fromString(startSession());
    return session;
  }

  // ---- entrées

  /** Instant situé à {@code days} jours avant l'instant fixé des tests (même heure du jour). */
  protected static Instant daysAgo(int days) {
    return NOW.minus(days, ChronoUnit.DAYS);
  }

  /** Jour UTC de l'instant fixé des tests. */
  protected static LocalDate today() {
    return LocalDate.ofInstant(NOW, ZoneOffset.UTC);
  }

  protected static Map<String, Object> exact(Instant from) {
    return Map.of("from", from.toString(), "precision", "exact");
  }

  /** Crée une entrée par l'API de clinical (corps libre) et renvoie son id. */
  protected UUID post(Map<String, Object> payload) throws Exception {
    return UUID.fromString(
        id(
            send(MockMvcRequestBuilders.post("/api/patients/{id}/entries", patient), payload)
                .andExpect(status().isCreated())));
  }

  protected Map<String, Object> base(String type, Instant from) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", type);
    payload.put("occurred", exact(from));
    payload.put("visibility", "clinical");
    return payload;
  }

  protected UUID feeling(String emotionCode, int intensity, Instant from) throws Exception {
    return feeling(emotionCode, intensity, from, "clinical", null);
  }

  protected UUID feeling(
      String emotionCode,
      int intensity,
      Instant from,
      String visibility,
      List<Map<String, Object>> links)
      throws Exception {
    Map<String, Object> payload = base("feeling", from);
    payload.put("visibility", visibility);
    payload.put("feeling", Map.of("emotionCode", emotionCode, "intensity", intensity));
    if (links != null) {
      payload.put("links", links);
    }
    return post(payload);
  }

  protected UUID situation(String title, Instant from) throws Exception {
    Map<String, Object> payload = base("situation", from);
    payload.put("title", title);
    payload.put("occurred", Map.of("from", from.toString()));
    return post(payload);
  }

  protected UUID note(String body, Instant from) throws Exception {
    Map<String, Object> payload = base("note", from);
    payload.put("body", body);
    return post(payload);
  }

  protected UUID medication(String molecule, String dosage, Instant from) throws Exception {
    Map<String, Object> payload = base("medication", from);
    payload.put(
        "medication",
        Map.of("molecule", molecule, "dosage", dosage, "frequency", "matin", "prescriber", "Dr X"));
    return post(payload);
  }

  protected void archive(UUID entryId) throws Exception {
    send(MockMvcRequestBuilders.delete("/api/entries/{id}", entryId), null)
        .andExpect(status().isNoContent());
  }

  /**
   * Fait comme un service d'écriture clinique pour un changement posé par SQL : incrémente {@code
   * patient.clinical_rev} (sans publier d'événement).
   */
  protected void touchRev() {
    jdbc.update("update patient set clinical_rev = clinical_rev + 1 where id = ?", patient);
  }

  protected long clinicalRev() {
    return jdbc.queryForObject(
        "select clinical_rev from patient where id = ?", Long.class, patient);
  }

  // ---- vue d'ensemble

  protected ResultActions overviewRaw(UUID patientId, String... nameValue) throws Exception {
    MockHttpServletRequestBuilder get =
        MockMvcRequestBuilders.get("/api/patients/{id}/overview", patientId);
    for (int i = 0; i < nameValue.length; i += 2) {
      get.queryParam(nameValue[i], nameValue[i + 1]);
    }
    return mvc.perform(get.with(auth));
  }

  protected DocumentContext overview(String... nameValue) throws Exception {
    return JsonPath.parse(body(overviewRaw(patient, nameValue).andExpect(status().isOk())));
  }

  protected int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  /**
   * Attend que toutes les publications du patient destinées aux abonnés de la vue d'ensemble soient
   * complétées. À appeler avant toute écriture SQL sur {@code patient_overview} : chaque entrée
   * créée par l'API (et la séance ouverte dans {@link #baseSetUp}) déclenche un recalcul asynchrone
   * qui réécrit la ligne entière, et qui écraserait la ligne préparée par le test s'il passait
   * entre son écriture et sa lecture (constaté sur la CI à 2 CPU). Le délai couvre aussi l'arriéré
   * laissé par les tests précédents (abonnés servis dans l'ordre d'arrivée).
   */
  protected void awaitOverviewQuiet() {
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                count(
                        "select count(*) from event_publication where listener_id like"
                            + " 'overview.%' and serialized_event like ?"
                            + " and completion_date is null",
                        "%" + patient + "%")
                    == 0);
  }

  /** payload jsonb de la ligne du patient, en texte normalisé par PostgreSQL ; null si absente. */
  protected String storedPayload(UUID patientId) {
    List<String> rows =
        jdbc.queryForList(
            "select payload::text from patient_overview where patient_id = ?",
            String.class,
            patientId);
    return rows.isEmpty() ? null : rows.get(0);
  }
}
