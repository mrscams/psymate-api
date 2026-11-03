package com.psymate.search;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * Socle des tests de la recherche : Postgres pgvector (Testcontainers), un cabinet, un praticien,
 * un patient et une séance ouverte par test ; les entrées sont créées par l'API de clinical (comme
 * en production) ou, pour les cas que l'API interdit, par SQL. Jamais de mock de la base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class SearchSupport {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected TransactionTemplate tx;

  protected UUID tenant;
  protected UUID me;
  protected UUID patient;
  protected UUID session;
  protected RequestPostProcessor auth;

  @BeforeEach
  protected void baseSetUp() throws Exception {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    me = newPractitioner();
    patient = newPatient(me);
    auth = as(me);
    session = UUID.fromString(startSession());
  }

  @AfterEach
  protected void clearSecurity() {
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

  protected String createTag(String label) throws Exception {
    return id(
        send(MockMvcRequestBuilders.post("/api/tags"), Map.of("label", label))
            .andExpect(status().isCreated()));
  }

  /** Crée une entrée par l'API de clinical et renvoie son id. */
  protected UUID entry(
      String type,
      String title,
      String body,
      Instant from,
      String visibility,
      List<String> tags,
      Map<String, Object> feeling)
      throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", type);
    if (title != null) {
      payload.put("title", title);
    }
    if (body != null) {
      payload.put("body", body);
    }
    payload.put(
        "occurred",
        Map.of("from", from.truncatedTo(ChronoUnit.SECONDS).toString(), "precision", "exact"));
    payload.put("visibility", visibility);
    if (tags != null) {
      payload.put("tags", tags);
    }
    if (feeling != null) {
      payload.put("feeling", feeling);
    }
    return UUID.fromString(
        id(
            send(MockMvcRequestBuilders.post("/api/patients/{id}/entries", patient), payload)
                .andExpect(status().isCreated())));
  }

  protected UUID note(String body, Instant from, String... tags) throws Exception {
    return entry("note", null, body, from, "clinical", List.of(tags), null);
  }

  protected UUID event(String title, String body, Instant from) throws Exception {
    return entry("event", title, body, from, "clinical", List.of(), null);
  }

  protected UUID feeling(String emotionCode, String body, Instant from, String... tags)
      throws Exception {
    return entry(
        "feeling",
        null,
        body,
        from,
        "clinical",
        List.of(tags),
        Map.of("emotionCode", emotionCode, "intensity", 6));
  }

  /**
   * Insère une entrée par SQL (cas que l'API interdit : auteur différent). L'entrée est rattachée à
   * la séance ouverte ; le trigger de V08 calcule son vecteur.
   */
  protected UUID sqlNote(
      UUID createdBy, String visibility, String title, String body, Instant from) {
    UUID entryId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    tx.executeWithoutResult(
        s -> {
          jdbc.update(
              "insert into entry (id, tenant_id, patient_id, type, visibility, occurred_from,"
                  + " occurred_to, session_id, current_version_id, created_by)"
                  + " values (?, ?, ?, 'note', ?, ?, ?, ?, ?, ?)",
              entryId,
              tenant,
              patient,
              visibility,
              Timestamp.from(from),
              Timestamp.from(from),
              session,
              versionId,
              createdBy);
          jdbc.update(
              "insert into entry_version (id, entry_id, version, title, body, author_id)"
                  + " values (?, ?, 1, ?, ?, ?)",
              versionId,
              entryId,
              title,
              body,
              createdBy);
        });
    return entryId;
  }

  protected void archive(UUID entryId) throws Exception {
    send(MockMvcRequestBuilders.delete("/api/entries/{id}", entryId), null)
        .andExpect(status().isNoContent());
  }

  // ---- recherche

  /** GET /api/patients/{patient}/search ; paires nom, valeur (un nom peut se répéter). */
  protected ResultActions searchRaw(UUID patientId, String... nameValue) throws Exception {
    MockHttpServletRequestBuilder get =
        MockMvcRequestBuilders.get("/api/patients/{id}/search", patientId);
    for (int i = 0; i < nameValue.length; i += 2) {
      get.queryParam(nameValue[i], nameValue[i + 1]);
    }
    return mvc.perform(get.with(auth));
  }

  protected DocumentContext search(String... nameValue) throws Exception {
    ResultActions result = searchRaw(patient, nameValue).andExpect(status().isOk());
    return JsonPath.parse(body(result));
  }

  protected static List<String> ids(DocumentContext doc) {
    return doc.read("$.items[*].entryId");
  }

  protected static List<String> asStrings(List<UUID> uuids) {
    List<String> out = new ArrayList<>();
    uuids.forEach(u -> out.add(u.toString()));
    return out;
  }

  protected int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }
}
