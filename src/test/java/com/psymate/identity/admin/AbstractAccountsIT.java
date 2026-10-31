package com.psymate.identity.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.psymate.identity.api.CurrentPractitioner;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outils communs aux tests d'administration des comptes (M1-14b) : comptes insérés en base avec un
 * vrai mot de passe haché, administrateurs avec 2FA, session réelle par /api/auth/login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminTestConfig.class)
abstract class AbstractAccountsIT {

  static final String PASSWORD = "Zebre-Tournesol-42";

  private static final AtomicInteger IP = new AtomicInteger();

  /** Parties de clés qui trahiraient une donnée patient, de clinique ou de secret. */
  static final List<String> FORBIDDEN_KEY_PARTS =
      List.of("patient", "entry", "storagekey", "sha256", "password", "totp", "secret");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AdminTestConfig.Recorder recorder;
  @Autowired GenericContainer<?> mailpit;
  @Autowired PasswordEncoder encoder;
  final JsonMapper json = JsonMapper.builder().build();
  AdminMailpit mails;
  UUID tenant;
  private static String passwordHash;

  @BeforeEach
  void setUpAccounts() {
    mails = new AdminMailpit(mailpit);
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    if (passwordHash == null) {
      passwordHash = encoder.encode(PASSWORD);
    }
  }

  // ---- comptes

  /** Compte confirmé, e-mail id@example.test, mot de passe PASSWORD. */
  UUID account(String displayName, String status, String role, boolean totp, String locale) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, email_confirmed_at,"
            + " password_hash, first_name, last_name, display_name, locale, profession,"
            + " address_city, verification_status, role, totp_secret_enc, totp_enabled_at,"
            + " verified_at) values (?, ?, ?, ?, now(), ?, 'Maya', 'Kaddour', ?, ?,"
            + " 'psychologist', 'Lyon', ?, ?, ?, ?, ?)",
        id,
        tenant,
        email,
        email,
        passwordHash,
        displayName,
        locale,
        status,
        role,
        totp ? new byte[] {1} : null,
        totp ? Timestamp.from(Instant.now()) : null,
        "approved".equals(status) ? Timestamp.from(Instant.now()) : null);
    return id;
  }

  UUID admin() {
    return account("Admin Test", "approved", "admin", true, "fr");
  }

  UUID adminWithoutTwoFactor() {
    return account("Admin Sans 2FA", "approved", "admin", false, "fr");
  }

  UUID practitioner(String displayName, String status) {
    return account(displayName, status, "practitioner", false, "fr");
  }

  String emailOf(UUID id) {
    return id + "@example.test";
  }

  // ---- authentification

  RequestPostProcessor as(UUID id, String role, String status) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            id, tenant, id + "@example.test", "Dr Test", "psychologist", status);
    List<SimpleGrantedAuthority> authorities =
        role == null ? List.of() : List.of(new SimpleGrantedAuthority(role));
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
  }

  RequestPostProcessor asAdmin(UUID id) {
    return as(id, "ROLE_ADMIN", "approved");
  }

  /** Adresse source unique par appel : le verrou de connexion est par e-mail + IP. */
  static String freshIp() {
    int n = IP.incrementAndGet();
    return "10.14." + (n / 250) + "." + (n % 250 + 1);
  }

  /** Connexion réelle : renvoie la réponse, dont le cookie SESSION en cas de succès. */
  MvcResult login(String email, String password) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", email);
    body.put("password", password);
    String ip = freshIp();
    return mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .with(
                    r -> {
                      r.setRemoteAddr(ip);
                      return r;
                    }))
        .andReturn();
  }

  Cookie sessionOf(UUID id) throws Exception {
    MvcResult r = login(emailOf(id), PASSWORD);
    assertThat(r.getResponse().getStatus()).as("login").isEqualTo(200);
    return r.getResponse().getCookie("SESSION");
  }

  MvcResult me(Cookie session) throws Exception {
    return mvc.perform(get("/api/auth/me").cookie(session)).andReturn();
  }

  // ---- utilitaires

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  /**
   * Texte du dernier e-mail reçu, avec une longue attente (l'envoi est asynchrone et parfois lent).
   */
  String mailText(String to, String subject) throws Exception {
    return mails.lastText(to, subject, Duration.ofSeconds(60));
  }

  int status(MvcResult r) {
    return r.getResponse().getStatus();
  }

  String code(MvcResult r) throws Exception {
    return body(r).path("code").asString();
  }

  String statusOf(UUID id) {
    return jdbc.queryForObject(
        "select verification_status from practitioner where id = ?", String.class, id);
  }

  int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  static boolean await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        return false;
      }
      Thread.sleep(50);
    }
    return true;
  }

  /** Aucune clé du JSON ne contient une partie interdite (donnée patient, secret, stockage). */
  void assertNoForbiddenKey(JsonNode node, String path) {
    if (node.isObject()) {
      for (var e : node.properties()) {
        String key = e.getKey().toLowerCase(java.util.Locale.ROOT);
        for (String forbidden : FORBIDDEN_KEY_PARTS) {
          assertThat(key).as("clé " + path + "." + e.getKey()).doesNotContain(forbidden);
        }
        assertNoForbiddenKey(e.getValue(), path + "." + e.getKey());
      }
    } else if (node.isArray()) {
      int i = 0;
      for (JsonNode child : node) {
        assertNoForbiddenKey(child, path + "[" + i++ + "]");
      }
    }
  }
}
