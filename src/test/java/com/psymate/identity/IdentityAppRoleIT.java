package com.psymate.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.persistence.DocumentAccessLog;
import com.psymate.identity.persistence.DocumentAccessLogJpaRepository;
import com.psymate.identity.persistence.SecurityEvent;
import com.psymate.identity.persistence.SecurityEventJpaRepository;
import com.psymate.identity.persistence.VerificationDecision;
import com.psymate.identity.persistence.VerificationDecisionJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Le module identity tourne ici avec le rôle psymate_app (Flyway avec le propriétaire) : les
 * écritures JPA du module passent là où le rôle a le droit, et les trois tables append-only
 * (security_event, verification_decision, document_access_log) acceptent l'INSERT mais refusent
 * UPDATE et DELETE, que les entités {@code @Immutable} ne tentent jamais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IdentityAppRoleIT.AppRoleConfig.class)
class IdentityAppRoleIT {

  /** Postgres (pgvector, init du rôle psymate_app), datasource sous psymate_app, et Mailpit. */
  @TestConfiguration(proxyBeanMethods = false)
  static class AppRoleConfig {
    @Bean(destroyMethod = "stop", initMethod = "start")
    PostgreSQLContainer postgres() {
      return new PostgreSQLContainer(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("psymate")
          .withUsername("psymate")
          .withPassword("psymate")
          .withCopyFileToContainer(
              MountableFile.forHostPath(Path.of("docker/postgres/init/01-app-role.sql")),
              "/docker-entrypoint-initdb.d/01-app-role.sql");
    }

    @Bean
    DynamicPropertyRegistrar roles(PostgreSQLContainer c) {
      return registry -> {
        registry.add("spring.datasource.url", c::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "psymate_app");
        registry.add("spring.datasource.password", () -> "psymate_app");
        registry.add("spring.flyway.url", c::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "psymate");
        registry.add("spring.flyway.password", () -> "psymate");
      };
    }

    @Bean
    GenericContainer<?> mailpit() {
      return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.32.0"))
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));
    }

    @Bean
    DynamicPropertyRegistrar mailProperties(GenericContainer<?> mailpit) {
      return registry -> {
        registry.add("spring.mail.host", mailpit::getHost);
        registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
      };
    }
  }

  private static final String PASSWORD = "Zebre-Tournesol-42";
  private static final String REASON =
      "Plusieurs signalements concordants sur l'exercice du praticien.";
  private static final String MESSAGE =
      "Je conteste cette décision : les éléments invoqués sont inexacts.";
  private static final byte[] PDF =
      ("%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n"
              + "contenu confidentiel diplome ".repeat(20))
          .getBytes(StandardCharsets.US_ASCII);

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate tx;
  @Autowired private GenericContainer<?> mailpit;
  @Autowired private EntityManagerFactory emf;
  @Autowired private SecurityEventJpaRepository securityEvents;
  @Autowired private VerificationDecisionJpaRepository decisions;
  @Autowired private DocumentAccessLogJpaRepository accessLogs;

  private final JsonMapper json = JsonMapper.builder().build();
  private final HttpClient http = HttpClient.newHttpClient();
  private UUID tenant;

  @BeforeEach
  void underTheApplicationRole() {
    assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("psymate_app");
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  // ---- fixtures

  private UUID insertPractitioner(String role, String status, boolean totp) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, email_confirmed_at,"
            + " password_hash, first_name, last_name, display_name, profession, role,"
            + " verification_status, totp_secret_enc, totp_enabled_at) values (?, ?, ?, ?, now(),"
            + " 'x', 'Test', 'Admin', 'Admin Test', 'psychologist', ?, ?, ?, ?)",
        id,
        tenant,
        email,
        email,
        role,
        status,
        totp ? new byte[] {1} : null,
        totp ? Timestamp.from(Instant.now()) : null);
    return id;
  }

  private RequestPostProcessor asAdmin(UUID id) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            id, tenant, id + "@example.test", "Admin Test", "psychologist", "approved");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  private int count(String sql, Object... args) {
    Integer n = jdbc.queryForObject(sql, Integer.class, args);
    return n == null ? 0 : n;
  }

  private String string(String sql, Object... args) {
    return jdbc.queryForObject(sql, String.class, args);
  }

  // ---- tables append-only : INSERT par l'entité, UPDATE et DELETE refusés au rôle

  @Test
  void appendOnlyTablesAcceptEntityInsertsAndRefuseUpdateAndDelete() throws Exception {
    UUID admin = insertPractitioner("admin", "approved", true);
    UUID subject = insertPractitioner("practitioner", "under_review", false);
    UUID doc = UUID.randomUUID();
    jdbc.update(
        "insert into verification_document (id, practitioner_id, type, title, storage_key, mime,"
            + " size_bytes, sha256) values (?, ?, 'diploma', 'Titre', ?, 'application/pdf', 10,"
            + " 'abc')",
        doc,
        subject,
        "verification/" + subject + "/" + doc + ".bin");
    Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
    UUID eventId = UUID.randomUUID();
    UUID decisionId = UUID.randomUUID();
    UUID logId = UUID.randomUUID();

    // INSERT autorisé, relu par l'entité puis en SQL brut.
    securityEvents.save(
        new SecurityEvent(
            eventId,
            subject,
            admin,
            "admin.decision",
            InetAddress.getByName("203.0.113.7"),
            Map.of("kind", "approved"),
            at));
    decisions.save(
        new VerificationDecision(
            decisionId,
            subject,
            admin,
            "approved",
            "note",
            "message",
            Map.of("ok", true),
            null,
            at));
    accessLogs.save(
        new DocumentAccessLog(logId, doc, admin, InetAddress.getByName("2001:db8::7"), at));

    assertThat(securityEvents.findById(eventId).orElseThrow().getDetails())
        .containsEntry("kind", "approved");
    assertThat(decisions.findById(decisionId).orElseThrow().getChecklist())
        .containsEntry("ok", true);
    assertThat(accessLogs.findById(logId).orElseThrow().getIp())
        .isEqualTo(InetAddress.getByName("2001:db8::7"));
    assertThat(string("select host(ip) from security_event where id = ?", eventId))
        .isEqualTo("203.0.113.7");

    // UPDATE et DELETE refusés par le rôle sur les trois tables.
    for (String[] table :
        new String[][] {
          {"security_event", "kind", eventId.toString()},
          {"verification_decision", "kind", decisionId.toString()},
          {"document_access_log", "accessed_at", logId.toString()}
        }) {
      String column = table[1];
      Object value = column.equals("accessed_at") ? Timestamp.from(at.plusSeconds(1)) : "x";
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "update " + table[0] + " set " + column + " = ? where id = ?::uuid",
                      value,
                      table[2]))
          .as("UPDATE " + table[0])
          .hasStackTraceContaining("permission denied");
      assertThatThrownBy(
              () -> jdbc.update("delete from " + table[0] + " where id = ?::uuid", table[2]))
          .as("DELETE " + table[0])
          .hasStackTraceContaining("permission denied");
    }
    assertThat(count("select count(*) from security_event where id = ?", eventId)).isEqualTo(1);
    assertThat(count("select count(*) from verification_decision where id = ?", decisionId))
        .isEqualTo(1);
    assertThat(count("select count(*) from document_access_log where id = ?", logId)).isEqualTo(1);
  }

  /**
   * Les entités {@code @Immutable} n'ont aucune méthode de modification : le seul « UPDATE » que le
   * code puisse tenter est un save d'un identifiant existant, qui est un INSERT (clé primaire
   * refusée), jamais un UPDATE. Un {@code delete} d'entité n'est pas ignoré par Hibernate : il émet
   * un DELETE que le rôle applicatif refuse (permission denied) et la ligne reste.
   */
  @Test
  void immutableEntitiesNeverEmitAnUpdateAndTheirDeleteIsRefusedByTheRole() {
    UUID subject = insertPractitioner("practitioner", "approved", false);
    UUID eventId = UUID.randomUUID();
    Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
    securityEvents.save(new SecurityEvent(eventId, subject, null, "login_ok", null, null, at));

    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    try {
      long updates = stats.getEntityUpdateCount();
      // Charger puis reenregistrer l'entité chargée ne produit aucun UPDATE.
      tx.executeWithoutResult(
          s -> securityEvents.save(securityEvents.findById(eventId).orElseThrow()));
      assertThat(stats.getEntityUpdateCount() - updates).isZero();

      // Un « save » d'une nouvelle instance de même identifiant est un INSERT : clé primaire.
      assertThatThrownBy(
              () ->
                  securityEvents.save(
                      new SecurityEvent(eventId, subject, null, "tampered", null, null, at)))
          .hasMessageContaining("security_event");

      // Suppression : Hibernate émet le DELETE, le rôle le refuse.
      assertThatThrownBy(
              () ->
                  tx.executeWithoutResult(
                      s -> securityEvents.delete(securityEvents.findById(eventId).orElseThrow())))
          .hasStackTraceContaining("permission denied");
    } finally {
      stats.setStatisticsEnabled(false);
    }
    assertThat(string("select kind from security_event where id = ?", eventId))
        .isEqualTo("login_ok");
  }

  // ---- écritures normales du module sous psymate_app

  private String confirmationToken(String to) throws Exception {
    String query = "to:" + to + " subject:\"confirmez\"";
    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
    while (System.nanoTime() < deadline) {
      JsonNode found =
          json.readTree(
              mailpitGet(
                  "/api/v1/search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)));
      if (!found.path("messages").isEmpty()) {
        String id = found.path("messages").get(0).path("ID").asString();
        String text = json.readTree(mailpitGet("/api/v1/message/" + id)).path("Text").asString();
        Matcher m = Pattern.compile("/confirmer-email/([A-Za-z0-9_-]+)").matcher(text);
        if (m.find()) {
          return m.group(1);
        }
      }
      Thread.sleep(100);
    }
    throw new AssertionError("aucun e-mail de confirmation reçu par " + to);
  }

  private String mailpitGet(String path) throws Exception {
    URI uri = URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025) + path);
    return http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString())
        .body();
  }

  private MvcResult postJson(String path, Object body, RequestPostProcessor who, String ip)
      throws Exception {
    var request =
        post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    if (who != null) {
      request.with(who);
    }
    if (ip != null) {
      request.with(
          r -> {
            r.setRemoteAddr(ip);
            return r;
          });
    }
    return mvc.perform(request).andReturn();
  }

  private MvcResult upload(Cookie session, String type) throws Exception {
    return mvc.perform(
            multipart("/api/me/verification/documents")
                .file(new MockMultipartFile("file", type + ".pdf", "application/pdf", PDF))
                .param("type", type)
                .param("title", "Master de psychologie")
                .param("issuer", "Université de Lyon")
                .param("year", "2012")
                .cookie(session))
        .andReturn();
  }

  @Test
  void theWholeIdentityLifecycleWritesUnderTheApplicationRole() throws Exception {
    String email = "role" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";

    // Inscription : tenant, praticien, jeton de confirmation, preuve des consentements.
    Map<String, Object> registration = new LinkedHashMap<>();
    registration.put("email", email);
    registration.put("password", PASSWORD);
    registration.put("firstName", "Maya");
    registration.put("lastName", "Kaddour");
    registration.put("profession", "psychologist");
    registration.put("phone", "06 12 34 56 78");
    registration.put(
        "address", Map.of("street", "12 rue des Lilas", "postalCode", "69003", "city", "Lyon"));
    registration.put("remoteOnly", false);
    registration.put("consents", Map.of("terms", true, "privacy", true));
    assertThat(
            postJson("/api/auth/register", registration, null, "10.31.0.1")
                .getResponse()
                .getStatus())
        .isEqualTo(201);
    UUID id =
        jdbc.queryForObject(
            "select id from practitioner where email_normalized = ?", UUID.class, email);
    assertThat(count("select count(*) from auth_token where practitioner_id = ?", id)).isEqualTo(1);
    assertThat(
            count(
                "select count(*) from security_event where practitioner_id = ? and kind ="
                    + " 'consent_given'",
                id))
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from tenant t join practitioner p on p.tenant_id = t.id"
                    + " where p.id = ?",
                id))
        .isEqualTo(1);

    // Confirmation d'e-mail : jeton consommé, statut docs_pending, session ouverte.
    MvcResult confirmed =
        postJson(
            "/api/auth/email/confirm",
            Map.of("token", confirmationToken(email)),
            null,
            "10.31.0.1");
    assertThat(confirmed.getResponse().getStatus()).isEqualTo(204);
    Cookie session = confirmed.getResponse().getCookie("SESSION");
    assertThat(session).isNotNull();
    assertThat(string("select verification_status from practitioner where id = ?", id))
        .isEqualTo("docs_pending");
    assertThat(
            count(
                "select count(*) from auth_token where practitioner_id = ? and consumed_at is not null",
                id))
        .isEqualTo(1);
    assertThat(count("select count(*) from spring_session where principal_name = ?", id.toString()))
        .isGreaterThanOrEqualTo(1);

    // PATCH des préférences : un seul document jsonb réécrit.
    MvcResult prefs =
        mvc.perform(
                patch("/api/me/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"theme\":\"dark\"}")
                    .cookie(session))
            .andReturn();
    assertThat(prefs.getResponse().getStatus()).isEqualTo(200);
    assertThat(string("select preferences->>'theme' from practitioner where id = ?", id))
        .isEqualTo("dark");

    // Justificatifs et soumission.
    assertThat(upload(session, "diploma").getResponse().getStatus()).isEqualTo(201);
    assertThat(upload(session, "identity").getResponse().getStatus()).isEqualTo(201);
    MvcResult submitted =
        mvc.perform(post("/api/me/verification/submit").cookie(session)).andReturn();
    assertThat(submitted.getResponse().getStatus()).isBetween(200, 204);
    assertThat(string("select verification_status from practitioner where id = ?", id))
        .isEqualTo("under_review");
    assertThat(count("select count(*) from verification_document where practitioner_id = ?", id))
        .isEqualTo(2);
    assertThat(
            count(
                "select count(*) from security_event where practitioner_id = ? and kind ="
                    + " 'verification_submitted'",
                id))
        .isEqualTo(1);

    // Décision d'un administrateur : examen d'un justificatif, décision approved.
    UUID admin = insertPractitioner("admin", "approved", true);
    UUID reviewer = insertPractitioner("admin", "approved", true);
    UUID document =
        jdbc.queryForObject(
            "select id from verification_document where practitioner_id = ? and type = 'diploma'",
            UUID.class,
            id);
    MvcResult review =
        mvc.perform(
                patch("/api/admin/verifications/" + id + "/documents/" + document)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reviewedStatus\":\"conform\"}")
                    .with(asAdmin(admin)))
            .andReturn();
    assertThat(review.getResponse().getStatus()).isEqualTo(200);
    assertThat(string("select reviewed_status from verification_document where id = ?", document))
        .isEqualTo("conform");
    MvcResult url =
        mvc.perform(
                get("/api/admin/verifications/" + id + "/documents/" + document + "/url")
                    .with(asAdmin(admin)))
            .andReturn();
    assertThat(url.getResponse().getStatus()).isEqualTo(200);
    String signed = json.readTree(url.getResponse().getContentAsString()).path("url").asString();
    assertThat(
            mvc.perform(get(URI.create(signed)).with(asAdmin(admin)))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(count("select count(*) from document_access_log where document_id = ?", document))
        .isEqualTo(1);
    Map<String, Object> decision = new LinkedHashMap<>();
    decision.put("kind", "approved");
    decision.put("force", true);
    decision.put("internalNote", "Pièces vérifiées hors plateforme");
    decision.put("checklist", Map.of("identity", true));
    MvcResult approved =
        postJson("/api/admin/verifications/" + id + "/decision", decision, asAdmin(admin), null);
    assertThat(approved.getResponse().getStatus()).isEqualTo(201);
    assertThat(string("select verification_status from practitioner where id = ?", id))
        .isEqualTo("approved");
    assertThat(
            count(
                "select count(*) from verification_decision where practitioner_id = ? and kind ="
                    + " 'approved'",
                id))
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from verification_document where practitioner_id = ? and"
                    + " purge_after is not null",
                id))
        .as("purge des fichiers planifiée")
        .isEqualTo(2);

    // Suspension : décision, journal d'administration, sessions du praticien révoquées.
    MvcResult suspended =
        postJson(
            "/api/admin/practitioners/" + id + "/suspend",
            Map.of("reason", REASON),
            asAdmin(admin),
            null);
    assertThat(suspended.getResponse().getStatus()).isEqualTo(200);
    assertThat(string("select verification_status from practitioner where id = ?", id))
        .isEqualTo("suspended");
    assertThat(count("select count(*) from spring_session where principal_name = ?", id.toString()))
        .as("sessions révoquées")
        .isZero();
    assertThat(
            count(
                "select count(*) from security_event where practitioner_id = ? and kind ="
                    + " 'admin.suspend' and actor_id = ?",
                id,
                admin))
        .isEqualTo(1);

    // Dépôt d'un recours par le praticien suspendu (sans session).
    Map<String, Object> appeal = new LinkedHashMap<>();
    appeal.put("email", email);
    appeal.put("password", PASSWORD);
    appeal.put("message", MESSAGE);
    MvcResult filed = postJson("/api/me/appeal", appeal, null, "10.31.0.2");
    assertThat(filed.getResponse().getStatus()).isEqualTo(201);
    UUID appealId =
        UUID.fromString(
            json.readTree(filed.getResponse().getContentAsString()).path("id").asString());
    assertThat(
            count(
                "select count(*) from practitioner_appeal where id = ? and reviewed_at is null",
                appealId))
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from security_event where practitioner_id = ? and kind ="
                    + " 'appeal.submitted' and actor_id is null",
                id))
        .isEqualTo(1);

    // Un autre administrateur examine le recours (accepted : le compte est réactivé).
    MvcResult decided =
        postJson(
            "/api/admin/appeals/" + appealId + "/decision",
            Map.of(
                "outcome", "accepted", "messageToPractitioner", "Suspension levée après examen."),
            asAdmin(reviewer),
            null);
    assertThat(decided.getResponse().getStatus()).isEqualTo(200);
    assertThat(string("select outcome from practitioner_appeal where id = ?", appealId))
        .isEqualTo("accepted");
    assertThat(string("select verification_status from practitioner where id = ?", id))
        .isEqualTo("approved");

    // Nouvelle suspension puis réactivation manuelle.
    assertThat(
            postJson(
                    "/api/admin/practitioners/" + id + "/suspend",
                    Map.of("reason", REASON),
                    asAdmin(admin),
                    null)
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    MvcResult reactivated =
        postJson(
            "/api/admin/practitioners/" + id + "/reactivate",
            Map.of("reason", REASON),
            asAdmin(admin),
            null);
    assertThat(reactivated.getResponse().getStatus()).isEqualTo(200);
    assertThat(string("select verification_status from practitioner where id = ?", id))
        .isEqualTo("approved");
    assertThat(
            count(
                "select count(*) from verification_decision where practitioner_id = ? and kind in"
                    + " ('suspended','reactivated','appeal_accepted')",
                id))
        .isEqualTo(4);
  }
}
