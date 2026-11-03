package com.psymate.clinical.export;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.PostgresTestConfig;
import com.psymate.identity.api.CurrentPractitioner;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Socle des tests d'export (M6-05) : Postgres pgvector (Testcontainers), horloge pilotable, comptes
 * avec un vrai mot de passe haché, patients, entrées créées par l'API. Jamais de mock de la base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, ExportTestSupport.ClockConfig.class})
public abstract class ExportTestSupport {

  protected static final String PASSWORD = "Zebre-Tournesol-42";

  private static final AtomicInteger IP = new AtomicInteger();

  /** Horloge dont le test choisit l'instant courant. */
  public static class MutableClock extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

    public void set(Instant instant) {
      now.set(instant);
    }

    public void advance(Duration d) {
      now.updateAndGet(i -> i.plus(d));
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockConfig {
    @Bean
    @Primary
    MutableClock exportTestClock() {
      return new MutableClock();
    }
  }

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected MutableClock clock;
  @Autowired private PasswordEncoder encoder;

  private static String passwordHash;

  protected UUID tenant;

  @BeforeEach
  void baseSetUp() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet export')", tenant);
    clock.set(Instant.now());
    if (passwordHash == null) {
      passwordHash = encoder.encode(PASSWORD);
    }
  }

  /**
   * Un test ne laisse jamais de job échu à la purge d'un autre (revue A-19, B3) : les exports
   * terminés du tenant du test sont marqués purgés, comme dans {@code ExportPersistenceIT}.
   */
  @AfterEach
  void clearSecurityAndNeutraliseExports() {
    SecurityContextHolder.clearContext();
    jdbc.update(
        "update export_job set purged_at = now() where purged_at is null and status = 'done'"
            + " and practitioner_id in (select id from practitioner where tenant_id = ?)",
        tenant);
  }

  // ---- comptes et patients

  /** Compte confirmé, e-mail id@example.test, mot de passe PASSWORD, statut de vérification. */
  protected UUID account(String displayName, String status) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, email_confirmed_at,"
            + " password_hash, first_name, last_name, display_name, locale, profession,"
            + " address_city, verification_status, role, verified_at) values (?, ?, ?, ?, now(),"
            + " ?, 'Maya', 'Kaddour', ?, 'fr', 'psychologist', 'Lyon', ?, 'practitioner', ?)",
        id,
        tenant,
        email,
        email,
        passwordHash,
        displayName,
        status,
        "approved".equals(status) ? Timestamp.from(Instant.now()) : null);
    return id;
  }

  protected String emailOf(UUID accountId) {
    return accountId + "@example.test";
  }

  /** Patient inséré directement (aucun événement, donc aucune séance d'accueil). */
  protected UUID patient(UUID owner, String displayName, LocalDate birthDate) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name, birth_date, phone, email)"
            + " values (?, ?, ?, ?, ?, '0612345678', 'contact-secret@example.test')",
        id,
        tenant,
        owner,
        displayName,
        birthDate);
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
            "approved");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  /** Adresse source unique par appel : le verrou de connexion est par e-mail + IP. */
  protected static String freshIp() {
    int n = IP.incrementAndGet();
    return "10.61." + (n / 250) + "." + (n % 250 + 1);
  }

  // ---- requêtes

  protected ResultActions send(
      MockHttpServletRequestBuilder builder, String body, RequestPostProcessor auth)
      throws Exception {
    if (body != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.getBytes(StandardCharsets.UTF_8));
    }
    return mvc.perform(auth == null ? builder : builder.with(auth));
  }

  protected ResultActions post(String url, String body, RequestPostProcessor auth)
      throws Exception {
    return send(MockMvcRequestBuilders.post(url), body, auth);
  }

  protected ResultActions patch(String url, String body, RequestPostProcessor auth)
      throws Exception {
    return send(MockMvcRequestBuilders.patch(url), body, auth);
  }

  protected ResultActions get(String url, RequestPostProcessor auth) throws Exception {
    return send(MockMvcRequestBuilders.get(url), null, auth);
  }

  protected static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  protected static String json(ResultActions result, String path) throws Exception {
    return JsonPath.read(body(result), path);
  }

  // ---- scénarios de base (le praticien est le principal de chaque appel)

  /** Démarre une séance à l'instant donné (horloge pilotée) et renvoie son id. */
  protected String startSession(UUID practitioner, UUID patient, Instant at) throws Exception {
    clock.set(at);
    return json(
        post(
                "/api/patients/" + patient + "/sessions/start",
                "{\"modality\":\"in_person\"}",
                as(practitioner))
            .andExpect(status().isCreated()),
        "$.id");
  }

  protected void closeSession(UUID practitioner, String session, String summary) throws Exception {
    post(
            "/api/sessions/" + session + "/close",
            "{\"durationMin\":50,\"summary\":\"" + summary + "\"}",
            as(practitioner))
        .andExpect(status().isOk());
  }

  /** Crée une entrée dans la séance et renvoie son id. */
  protected String createEntry(UUID practitioner, UUID patient, String session, String json)
      throws Exception {
    String withSession = json.replaceFirst("^\\{", "{\"sessionId\":\"" + session + "\",");
    return json(
        post("/api/patients/" + patient + "/entries", withSession, as(practitioner))
            .andExpect(status().isCreated()),
        "$.id");
  }

  protected String createTag(UUID practitioner, String label) throws Exception {
    return json(
        post("/api/tags", "{\"label\":\"" + label + "\"}", as(practitioner))
            .andExpect(status().isCreated()),
        "$.id");
  }

  protected int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }
}
