package com.psymate.clinical.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.clinical.ClinicalFixtures;
import com.psymate.clinical.ClinicalTestConfig;
import com.psymate.clinical.ClinicalTestConfig.MutableClock;
import com.psymate.identity.api.CurrentPractitioner;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

/**
 * Socle des tests REST de clinical : Postgres pgvector (Testcontainers), horloge pilotable, un
 * praticien et un patient, requêtes MockMvc authentifiées. Jamais de mock de la base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ClinicalTestConfig.class)
abstract class ClinicalWebSupport {

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected MutableClock clock;

  protected ClinicalFixtures fx;
  protected UUID patient;
  protected RequestPostProcessor me;

  @BeforeEach
  void baseSetUp() {
    fx = new ClinicalFixtures(jdbc);
    // Les requêtes portent leur propre principal ; rien ne doit traîner sur le thread de test.
    SecurityContextHolder.clearContext();
    patient = fx.newPatient(LocalDate.of(1990, 4, 2));
    me = as(fx.practitioner);
    clock.set(Instant.now());
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  protected RequestPostProcessor as(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            fx.tenant,
            practitionerId + "@example.test",
            "Dr Test",
            "psychologist",
            "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  // ---- requêtes

  protected ResultActions send(MockHttpServletRequestBuilder builder, String body)
      throws Exception {
    return send(builder, body, me);
  }

  protected ResultActions send(
      MockHttpServletRequestBuilder builder, String body, RequestPostProcessor auth)
      throws Exception {
    if (body != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.getBytes(StandardCharsets.UTF_8));
    }
    return mvc.perform(builder.with(auth));
  }

  protected ResultActions post(String url, String body) throws Exception {
    return send(MockMvcRequestBuilders.post(url), body);
  }

  protected ResultActions patch(String url, String body) throws Exception {
    return send(MockMvcRequestBuilders.patch(url), body);
  }

  protected ResultActions get(String url) throws Exception {
    return send(MockMvcRequestBuilders.get(url), null);
  }

  protected ResultActions delete(String url) throws Exception {
    return send(MockMvcRequestBuilders.delete(url), null);
  }

  protected static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  protected static String idOf(ResultActions result) throws Exception {
    return JsonPath.read(body(result), "$.id");
  }

  // ---- scénarios de base

  protected String entriesUrl(UUID patientId) {
    return "/api/patients/" + patientId + "/entries";
  }

  /** Démarre une séance du patient courant par l'API et renvoie son id. */
  protected String startSession(UUID patientId) throws Exception {
    return idOf(
        post("/api/patients/" + patientId + "/sessions/start", "{\"modality\":\"in_person\"}")
            .andExpect(status().isCreated()));
  }

  /** Crée une entrée et renvoie son id (le test attend un 201). */
  protected String createEntry(UUID patientId, String json) throws Exception {
    ResultActions result = post(entriesUrl(patientId), json).andExpect(status().isCreated());
    return idOf(result);
  }

  protected String createTag(String label) throws Exception {
    return idOf(post("/api/tags", "{\"label\":\"" + label + "\"}").andExpect(status().isCreated()));
  }

  protected int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }
}
