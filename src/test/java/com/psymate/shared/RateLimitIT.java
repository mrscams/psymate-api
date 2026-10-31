package com.psymate.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.PostgresTestConfig;
import com.psymate.shared.ratelimit.RateLimiter;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Limitation de débit de bout en bout (M7-02, G Q24) : IP sur l'annuaire public, IP sur le contact,
 * session sur le reste de l'API. Les 600 requêtes de la règle « session » passent par une vraie
 * session (connexion), car le filtre lit l'identifiant de la session HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class RateLimitIT {

  private static final AtomicInteger IP = new AtomicInteger();
  private static final String PASSWORD = "Zebre-Tournesol-42";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PasswordEncoder encoder;
  @Autowired private RateLimiter limiter;

  private static String freshIp() {
    int n = IP.incrementAndGet();
    return "10.77." + (n / 250) + "." + (n % 250 + 1);
  }

  private static MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder b) {
    return b.with(
        r -> {
          r.setRemoteAddr(ip);
          return r;
        });
  }

  @Test
  void sixtyFirstPublicRequestFromOneIpGets429WithRetryAfter() throws Exception {
    String ip = freshIp();
    for (int i = 0; i < 60; i++) {
      mvc.perform(from(ip, get("/api/public/directory/practitioners/absent")))
          .andExpect(status().isNotFound());
    }
    mvc.perform(from(ip, get("/api/public/directory")))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(header().exists("Retry-After"))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Too Many Requests"))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Trop de requêtes")))
        .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    // Une autre adresse n'est pas touchée.
    mvc.perform(from(freshIp(), get("/api/public/directory"))).andExpect(status().isOk());
  }

  @Test
  void sixthContactFromOneIpGets429AndReadsKeepTheirOwnBucket() throws Exception {
    String ip = freshIp();
    String url = "/api/public/directory/practitioners/absent/contact";
    for (int i = 0; i < 5; i++) {
      int status =
          mvc.perform(from(ip, post(url)).contentType(MediaType.APPLICATION_JSON).content("{}"))
              .andReturn()
              .getResponse()
              .getStatus();
      assertThat(status).isNotEqualTo(429);
    }
    mvc.perform(from(ip, post(url)).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    // Le seau « contact » est distinct de celui des lectures : un GET passe encore.
    mvc.perform(from(ip, get("/api/public/directory"))).andExpect(status().isOk());
  }

  @Test
  void sessionBeyond600PerMinuteGets429AndAnotherSessionIsUnaffected() throws Exception {
    Cookie busy = login();
    Cookie other = login();
    // Une requête sans session valide (401) ne consomme aucun jeton : la règle passe après l'auth.
    int before = limiter.tracked("session");
    mvc.perform(get("/api/nope").cookie(new Cookie("SESSION", "inconnue")))
        .andExpect(status().isUnauthorized());
    assertThat(limiter.tracked("session")).isEqualTo(before);

    for (int i = 0; i < 600; i++) {
      int status = mvc.perform(get("/api/nope").cookie(busy)).andReturn().getResponse().getStatus();
      assertThat(status).as("requête %d", i + 1).isEqualTo(404);
    }
    MvcResult refused =
        mvc.perform(get("/api/nope").cookie(busy))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
            .andReturn();
    long retryAfter = Long.parseLong(refused.getResponse().getHeader("Retry-After"));
    assertThat(retryAfter).isBetween(1L, 60L);

    // L'autre session a son propre seau.
    mvc.perform(get("/api/nope").cookie(other)).andExpect(status().isNotFound());
  }

  /** Crée un praticien de test et ouvre une vraie session (cookie SESSION). */
  private Cookie login() throws Exception {
    UUID tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, ?, 'Test', 'Praticien', 'Dr Test', 'psychologist',"
            + " 'docs_pending')",
        id,
        tenant,
        email,
        email,
        encoder.encode(PASSWORD));
    MvcResult login =
        mvc.perform(
                from(
                    freshIp(),
                    post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")))
            .andExpect(status().isOk())
            .andReturn();
    return login.getResponse().getCookie("SESSION");
  }
}
