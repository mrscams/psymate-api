package com.psymate.identity.auth;

import com.psymate.identity.auth.AuthTestConfig.MutableClock;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Outils communs aux tests de connexion durcie et de 2FA (M1-11). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthTestConfig.class)
abstract class AbstractLoginIT {

  static final String PASSWORD = "Zebre-Tournesol-42";
  static final String IP = "203.0.113.10";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired MutableClock clock;
  final JsonMapper json = JsonMapper.builder().build();

  @BeforeEach
  void resetClock() {
    clock.reset();
  }

  static String newEmail() {
    return "l" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
  }

  /** Inscrit un compte puis fixe son statut de vérification. */
  String account(String status) throws Exception {
    String email = newEmail();
    String body =
        json.writeValueAsString(RegistrationFixtures.payload(email, PASSWORD, "psychologist"));
    mvc.perform(
            MockMvcRequestBuilders.post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
    jdbc.update(
        "update practitioner set verification_status = ? where email_normalized = ?",
        status,
        email);
    return email;
  }

  String account() throws Exception {
    return account("docs_pending");
  }

  UUID idOf(String email) {
    return jdbc.queryForObject(
        "select id from practitioner where email_normalized = ?", UUID.class, email);
  }

  MvcResult post(String url, Object body, Cookie cookie, String ip) throws Exception {
    var req =
        MockMvcRequestBuilders.post(url)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body == null ? "{}" : json.writeValueAsString(body))
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                });
    if (cookie != null) {
      req.cookie(cookie);
    }
    return mvc.perform(req).andReturn();
  }

  MvcResult login(String email, String password, String otp, String ip) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("email", email);
    body.put("password", password);
    if (otp != null) {
      body.put("otp", otp);
    }
    return post("/api/auth/login", body, null, ip);
  }

  Cookie session(String email) throws Exception {
    MvcResult r = login(email, PASSWORD, null, IP);
    if (r.getResponse().getStatus() != 200) {
      throw new AssertionError("login status " + r.getResponse().getStatus());
    }
    return r.getResponse().getCookie("SESSION");
  }

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  int events(String email, String kind) {
    return jdbc.queryForObject(
        "select count(*) from security_event e join practitioner p on p.id = e.practitioner_id"
            + " where p.email_normalized = ? and e.kind = ?",
        Integer.class,
        email,
        kind);
  }
}
