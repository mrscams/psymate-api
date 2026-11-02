package com.psymate.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.psymate.identity.model.AuthToken;
import com.psymate.identity.model.Practitioner;
import com.psymate.identity.model.SecurityEvent;
import com.psymate.identity.model.TotpRecoveryCode;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persistance JPA du module identity, lot auth / preferences / bootstrap (M8-05) : conflit
 * d'unicité attrapé au flush, journal de sécurité (inet, jsonb), consommations atomiques (jeton,
 * code de secours), UPDATE jsonb ciblé, et UPDATE dynamique de l'entité praticien.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IdentityPersistenceIT.Containers.class)
class IdentityPersistenceIT {

  /** Postgres (pgvector, rôle psymate_app) et Mailpit, comme AuthTestConfig. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Containers {
    @Bean
    @ServiceConnection
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

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;
  @Autowired private TransactionTemplate tx;
  @Autowired private GenericContainer<?> mailpit;
  @Autowired private PractitionerJpaRepository practitioners;
  @Autowired private SecurityEventJpaRepository securityEvents;
  @Autowired private AuthTokenJpaRepository authTokens;
  @Autowired private TotpRecoveryCodeJpaRepository recoveryCodes;

  private final JsonMapper json = JsonMapper.builder().build();
  private final HttpClient http = HttpClient.newHttpClient();

  // ---- fixtures

  private UUID newTenant() {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", id);
    return id;
  }

  /** Praticien créé par l'entité (fabrique bootstrapAdmin), prêt à porter des lignes liées. */
  private UUID newPractitioner() {
    UUID tenant = newTenant();
    UUID id = UUID.randomUUID();
    String email = "p" + id.toString().substring(0, 8) + "@example.org";
    practitioners.save(
        Practitioner.bootstrapAdmin(id, tenant, email, "hash", "Test", Instant.now()));
    return id;
  }

  private static String newEmail() {
    return "ip" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
  }

  // ---- (a) inscription concurrente : conflit d'unicité au flush

  private static final String PENDING_PRACTITIONER =
      "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
          + " first_name, last_name, display_name, profession) values"
          + " (?, ?, ?, ?, 'hash', 'Autre', 'Compte', 'Autre Compte', 'psychologist')";

  @Test
  void concurrentRegistrationWithTheSameEmailIsHandledSilentlyWhenTheUniqueIndexRefusesAtFlush()
      throws Exception {
    String email = newEmail();
    UUID otherTenant = newTenant();
    UUID otherId = UUID.randomUUID();
    int tenantsBefore = count("select count(*) from tenant");

    MvcResult result;
    try (Connection pending = dataSource.getConnection()) {
      pending.setAutoCommit(false);
      try (PreparedStatement ps = pending.prepareStatement(PENDING_PRACTITIONER)) {
        ps.setObject(1, otherId);
        ps.setObject(2, otherTenant);
        ps.setString(3, email);
        ps.setString(4, email);
        ps.executeUpdate();
      }
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        Future<MvcResult> call = pool.submit(() -> register(email));
        awaitOneBackendWaitingOnALock();
        pending.commit();
        result = call.get(30, TimeUnit.SECONDS);
      } finally {
        pool.shutdownNow();
      }
    }

    // Même réponse que pour une adresse libre : aucune 500, aucun détail.
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    // La transaction d'inscription est annulée en bloc : un seul compte, aucune trace du perdant.
    assertThat(count("select count(*) from practitioner where email_normalized = '" + email + "'"))
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from auth_token t join practitioner p on p.id = t.practitioner_id"
                    + " where p.email_normalized = '"
                    + email
                    + "'"))
        .isZero();
    assertThat(
            count("select count(*) from security_event where practitioner_id = '" + otherId + "'"))
        .isZero();
    // Un seul tenant ajouté : celui du compte annexe (le tenant de l'inscription est annulé).
    assertThat(count("select count(*) from tenant")).isEqualTo(tenantsBefore);
    // L'adresse reçoit l'e-mail « compte existant », pas de confirmation.
    List<String> subjects = subjectsReceivedBy(email);
    assertThat(subjects).hasSize(1);
    assertThat(subjects.get(0)).contains("déjà un compte");
  }

  private MvcResult register(String email) throws Exception {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("email", email);
    p.put("password", "Zebre-Tournesol-42");
    p.put("firstName", "Maya");
    p.put("lastName", "Kaddour");
    p.put("profession", "psychologist");
    p.put("phone", "06 12 34 56 78");
    p.put("address", Map.of("street", "12 rue des Lilas", "postalCode", "69003", "city", "Lyon"));
    p.put("remoteOnly", false);
    p.put("consents", Map.of("terms", true, "privacy", true));
    return mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(p)))
        .andReturn();
  }

  private void awaitOneBackendWaitingOnALock() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      Integer waiting =
          jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname = current_database()"
                  + " and wait_event_type = 'Lock'",
              Integer.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(20);
    }
    throw new AssertionError("l'inscription ne s'est jamais bloquée sur l'index unique");
  }

  private List<String> subjectsReceivedBy(String to) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    List<String> subjects = new ArrayList<>();
    while (System.nanoTime() < deadline) {
      URI uri =
          URI.create(
              "http://"
                  + mailpit.getHost()
                  + ":"
                  + mailpit.getMappedPort(8025)
                  + "/api/v1/search?query="
                  + URLEncoder.encode("to:" + to, StandardCharsets.UTF_8));
      JsonNode found =
          json.readTree(
              http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString())
                  .body());
      subjects.clear();
      found.path("messages").forEach(m -> subjects.add(m.path("Subject").asString()));
      if (!subjects.isEmpty()) {
        Thread.sleep(1500); // laisse arriver un éventuel second e-mail (qui serait une erreur)
        subjects.clear();
        json.readTree(
                http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString())
                    .body())
            .path("messages")
            .forEach(m -> subjects.add(m.path("Subject").asString()));
        return subjects;
      }
      Thread.sleep(100);
    }
    return subjects;
  }

  private int count(String sql) {
    Integer n = jdbc.queryForObject(sql, Integer.class);
    return n == null ? 0 : n;
  }

  // ---- (a bis) changement d'adresse concurrent : conflit d'unicité au flush

  /**
   * Le pré-contrôle de changeEmail ne voit pas une adresse prise par une transaction non validée :
   * c'est l'index ux_practitioner_email qui refuse, au commit du TransactionTemplate. Même issue
   * silencieuse que pour l'inscription : 202, adresse inchangée, e-mail « compte existant ».
   */
  @Test
  void concurrentEmailChangeToAnAddressTakenAtFlushIsHandledSilently() throws Exception {
    String email = newEmail();
    String wanted = newEmail();
    assertThat(register(email).getResponse().getStatus()).isEqualTo(201);
    // Le jeton d'inscription doit dater de plus d'une minute (RESEND_MIN_INTERVAL).
    jdbc.update(
        "update auth_token set created_at = created_at - interval '2 minutes'"
            + " where practitioner_id = (select id from practitioner where email_normalized = ?)",
        email);
    UUID otherTenant = newTenant();
    UUID otherId = UUID.randomUUID();

    int status;
    try (Connection pending = dataSource.getConnection()) {
      pending.setAutoCommit(false);
      try (PreparedStatement ps = pending.prepareStatement(PENDING_PRACTITIONER)) {
        ps.setObject(1, otherId);
        ps.setObject(2, otherTenant);
        ps.setString(3, wanted);
        ps.setString(4, wanted);
        ps.executeUpdate();
      }
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try {
        Future<Integer> call = pool.submit(() -> changeEmail(email, wanted));
        awaitOneBackendWaitingOnALock();
        pending.commit();
        status = call.get(30, TimeUnit.SECONDS);
      } finally {
        pool.shutdownNow();
      }
    }

    assertThat(status).isEqualTo(202);
    // Le compte garde son adresse ; l'adresse convoitée appartient au compte annexe.
    assertThat(count("select count(*) from practitioner where email_normalized = '" + email + "'"))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select id from practitioner where email_normalized = ?", UUID.class, wanted))
        .isEqualTo(otherId);
    // Aucun jeton de confirmation vers la nouvelle adresse n'a survécu au rollback.
    assertThat(
            count(
                "select count(*) from auth_token t join practitioner p on p.id = t.practitioner_id"
                    + " where p.email_normalized = '"
                    + email
                    + "' and t.consumed_at is null"))
        .isEqualTo(1);
    List<String> subjects = subjectsReceivedBy(wanted);
    assertThat(subjects).hasSize(1);
    assertThat(subjects.get(0)).contains("déjà un compte");
  }

  private int changeEmail(String email, String newAddress) throws Exception {
    return mvc.perform(
            post("/api/auth/email/change")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email", email,
                            "password", "Zebre-Tournesol-42",
                            "newEmail", newAddress))))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  // ---- (b) security_event : inet et jsonb par l'entité

  @Test
  void securityEventIsInsertedByTheEntityAndReadBackWithItsInetAndJsonbColumns() throws Exception {
    UUID practitioner = newPractitioner();
    UUID v4 = UUID.randomUUID();
    UUID v6 = UUID.randomUUID();
    UUID noIp = UUID.randomUUID();
    Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("reason", "bad_password");
    details.put("count", 3);
    details.put("flags", List.of(true, false));
    details.put("nested", Map.of("k", "é"));

    securityEvents.save(
        new SecurityEvent(
            v4,
            practitioner,
            null,
            "login_failed",
            InetAddress.getByName("203.0.113.7"),
            details,
            at));
    securityEvents.save(
        new SecurityEvent(
            v6, practitioner, null, "login_ok", InetAddress.getByName("2001:db8::7"), null, at));
    securityEvents.save(
        new SecurityEvent(noIp, practitioner, null, "email_confirmed", null, null, at));

    // Ce que la base a stocké (SQL brut, indépendant du mapping).
    Map<String, Object> raw =
        jdbc.queryForMap(
            "select host(ip) as ip, family(ip) as fam, details->>'reason' as reason,"
                + " (details->>'count')::int as cnt, details->'flags' as flags,"
                + " details->'nested'->>'k' as nested, occurred_at from security_event where id = ?",
            v4);
    assertThat(raw.get("ip")).isEqualTo("203.0.113.7");
    assertThat(raw.get("fam")).isEqualTo(4);
    assertThat(raw.get("reason")).isEqualTo("bad_password");
    assertThat(raw.get("cnt")).isEqualTo(3);
    assertThat(raw.get("nested")).isEqualTo("é");
    assertThat(
            jdbc.queryForObject(
                "select host(ip) from security_event where id = ?", String.class, v6))
        .isEqualTo("2001:db8::7");
    assertThat(
            jdbc.queryForObject(
                "select ip is null and details is null from security_event where id = ?",
                Boolean.class,
                noIp))
        .isTrue();

    // Relecture par l'entité : même adresse, mêmes détails, même instant.
    SecurityEvent read = securityEvents.findById(v4).orElseThrow();
    assertThat(read.getIp()).isEqualTo(InetAddress.getByName("203.0.113.7"));
    assertThat(read.getDetails())
        .containsEntry("reason", "bad_password")
        .containsEntry("count", 3)
        .containsEntry("flags", List.of(true, false))
        .containsEntry("nested", Map.of("k", "é"));
    assertThat(read.getOccurredAt().toInstant()).isEqualTo(at);
    assertThat(securityEvents.findById(v6).orElseThrow().getIp())
        .isEqualTo(InetAddress.getByName("2001:db8::7"));
    SecurityEvent bare = securityEvents.findById(noIp).orElseThrow();
    assertThat(bare.getIp()).isNull();
    assertThat(bare.getDetails()).isNull();
  }

  // ---- (c) consommation atomique d'un jeton

  @Test
  void anAuthTokenIsConsumedExactlyOnceSequentiallyAndUnderConcurrency() throws Exception {
    UUID practitioner = newPractitioner();
    Instant now = Instant.now();

    authTokens.save(
        new AuthToken(
            UUID.randomUUID(),
            practitioner,
            "email_confirm",
            "hash-seq",
            now.plusSeconds(600),
            now));
    Optional<UUID> first = authTokens.consume("email_confirm", "hash-seq", now);
    Optional<UUID> second = authTokens.consume("email_confirm", "hash-seq", now);
    assertThat(first).contains(practitioner);
    assertThat(second).isEmpty();

    authTokens.save(
        new AuthToken(
            UUID.randomUUID(),
            practitioner,
            "email_confirm",
            "hash-race",
            now.plusSeconds(600),
            now));
    int workers = 8;
    ExecutorService pool = Executors.newFixedThreadPool(workers);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Optional<UUID>>> results = new ArrayList<>();
      for (int i = 0; i < workers; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return authTokens.consume("email_confirm", "hash-race", now);
                }));
      }
      start.countDown();
      int winners = 0;
      for (Future<Optional<UUID>> f : results) {
        if (f.get(30, TimeUnit.SECONDS).isPresent()) {
          winners++;
        }
      }
      assertThat(winners).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }

    // Expiré ou d'un autre type : rien n'est consommé.
    authTokens.save(
        new AuthToken(
            UUID.randomUUID(),
            practitioner,
            "password_reset",
            "hash-old",
            now.minusSeconds(1),
            now.minusSeconds(60)));
    assertThat(authTokens.consume("password_reset", "hash-old", now)).isEmpty();
    authTokens.save(
        new AuthToken(
            UUID.randomUUID(),
            practitioner,
            "password_reset",
            "hash-kind",
            now.plusSeconds(600),
            now));
    assertThat(authTokens.consume("email_confirm", "hash-kind", now)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select consumed_at is null from auth_token where token_hash = 'hash-kind'",
                Boolean.class))
        .isTrue();
  }

  // ---- (d) code de secours : vrai une seule fois

  @Test
  void aRecoveryCodeIsConsumedExactlyOnce() throws Exception {
    UUID practitioner = newPractitioner();
    Instant now = Instant.now();
    recoveryCodes.save(new TotpRecoveryCode(UUID.randomUUID(), practitioner, "code-a", now));
    recoveryCodes.save(new TotpRecoveryCode(UUID.randomUUID(), practitioner, "code-b", now));
    recoveryCodes.save(new TotpRecoveryCode(UUID.randomUUID(), practitioner, "code-c", now));

    var odt = now.atOffset(java.time.ZoneOffset.UTC);
    assertThat(recoveryCodes.consumeRecoveryCode(practitioner, "code-a", odt)).isEqualTo(1);
    assertThat(recoveryCodes.consumeRecoveryCode(practitioner, "code-a", odt)).isZero();
    assertThat(recoveryCodes.consumeRecoveryCode(practitioner, "inconnu", odt)).isZero();

    int workers = 8;
    ExecutorService pool = Executors.newFixedThreadPool(workers);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < workers; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return recoveryCodes.consumeRecoveryCode(practitioner, "code-b", odt);
                }));
      }
      start.countDown();
      int total = 0;
      for (Future<Integer> f : results) {
        total += f.get(30, TimeUnit.SECONDS);
      }
      assertThat(total).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }

    // Il reste un seul code actif : retirer les codes ne supprime aucune ligne.
    assertThat(recoveryCodes.retireRecoveryCodes(practitioner, odt)).isEqualTo(1);
    assertThat(
            count(
                "select count(*) from totp_recovery_code where practitioner_id = '"
                    + practitioner
                    + "'"))
        .isEqualTo(3);
    assertThat(
            count(
                "select count(*) from totp_recovery_code where used_at is null and practitioner_id = '"
                    + practitioner
                    + "'"))
        .isZero();
  }

  // ---- (e) opérateur jsonb ciblé

  @Test
  void clearMustChangePasswordRemovesOnlyThatKey() {
    UUID id = newPractitioner();
    tx.executeWithoutResult(
        s -> {
          Practitioner p = practitioners.findForUpdate(id).orElseThrow();
          Map<String, Object> prefs = new LinkedHashMap<>(p.getPreferences());
          prefs.put("appearance.theme", "dark");
          p.replacePreferences(prefs);
        });
    assertThat(
            jdbc.queryForObject(
                "select preferences::text from practitioner where id = ?", String.class, id))
        .contains("security.mustChangePassword")
        .contains("appearance.theme");

    practitioners.clearMustChangePassword(id);

    Map<String, Object> stored = preferencesOf(id);
    assertThat(stored).doesNotContainKey("security.mustChangePassword");
    assertThat(stored).containsEntry("appearance.theme", "dark");
  }

  @Test
  void clearMustChangePasswordRefreshesAnEntityAlreadyLoadedInTheTransaction() {
    UUID id = newPractitioner();
    tx.executeWithoutResult(
        s -> {
          Practitioner loaded = practitioners.findById(id).orElseThrow();
          assertThat(loaded.getPreferences()).containsKey("security.mustChangePassword");
          loaded.changePassword("autre-hash", Instant.now());
          practitioners.clearMustChangePassword(id);
          assertThat(loaded.getPreferences()).doesNotContainKey("security.mustChangePassword");
        });
    // Le changement de mot de passe en attente a bien été écrit avant le UPDATE jsonb.
    assertThat(
            jdbc.queryForObject(
                "select password_hash from practitioner where id = ?", String.class, id))
        .isEqualTo("autre-hash");
    assertThat(preferencesOf(id)).doesNotContainKey("security.mustChangePassword");
  }

  private Map<String, Object> preferencesOf(UUID id) {
    String raw =
        jdbc.queryForObject(
            "select preferences::text from practitioner where id = ?", String.class, id);
    return json.readValue(raw, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
  }

  // ---- @DynamicUpdate : un UPDATE d'entité n'écrase pas une colonne écrite entre-temps

  @Test
  void anEntityUpdateOnlyWritesItsOwnColumnsAndKeepsAConcurrentCounterWrite() throws Exception {
    UUID id = newPractitioner();
    tx.executeWithoutResult(
        s -> {
          Practitioner p = practitioners.findById(id).orElseThrow();
          // Un autre flux (connexion annexe, autocommit) incrémente le compteur après le
          // chargement.
          try (Connection other = dataSource.getConnection();
              Statement st = other.createStatement()) {
            st.executeUpdate("update practitioner set failed_logins = 3 where id = '" + id + "'");
          } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
          }
          p.changePassword("nouveau-hash", Instant.now());
        });
    Map<String, Object> row =
        jdbc.queryForMap("select password_hash, failed_logins from practitioner where id = ?", id);
    assertThat(row.get("password_hash")).isEqualTo("nouveau-hash");
    assertThat(((Number) row.get("failed_logins")).intValue()).isEqualTo(3);
  }
}
