package com.psymate.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.PostgresTestConfig;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.shared.audit.AuditEvent;
import com.psymate.shared.audit.AuditWriter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Journal d'audit applicatif (M7-02, G Q29) : un accès patient = une ligne security_event. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class AuditIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private AuditWriter writer;

  private UUID practitioner;
  private RequestPostProcessor auth;

  @BeforeEach
  void practitioner() {
    UUID tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    practitioner = UUID.randomUUID();
    String email = practitioner + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist')",
        practitioner,
        tenant,
        email,
        email);
    CurrentPractitioner principal =
        new CurrentPractitioner(practitioner, tenant, email, "Dr Test", "psychologist", "none");
    auth =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String body) throws Exception {
    if (body != null) {
      b.contentType(MediaType.APPLICATION_JSON).content(body);
    }
    return mvc.perform(b.with(auth));
  }

  private String createPatient() throws Exception {
    String res =
        send(post("/api/patients"), "{\"displayName\":\"Audit\"}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(res, "$.id");
  }

  private int auditRows() {
    return jdbc.queryForObject(
        "select count(*) from security_event where practitioner_id = ? and kind like 'patient.%'",
        Integer.class, practitioner);
  }

  private void awaitRows(int expected) {
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(auditRows()).isEqualTo(expected));
  }

  @Test
  void readAndWritePatientAreJournaled() throws Exception {
    String id = createPatient();
    // Créer un patient (POST /api/patients) n'est pas un accès à un patient existant.
    assertThat(auditRows()).isZero();

    send(get("/api/patients/" + id).header("User-Agent", "AuditIT/1.0"), null)
        .andExpect(status().isOk());
    send(patch("/api/patients/" + id), "{\"phone\":\"0102\"}").andExpect(status().isOk());
    awaitRows(2);

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select kind, actor_id, host(ip) as ip, user_agent, details->>'patientId' as patient_id,"
                + " details->>'method' as method, details->>'path' as path,"
                + " details->>'status' as status from security_event"
                + " where practitioner_id = ? and kind like 'patient.%' order by occurred_at",
            practitioner);
    assertThat(rows)
        .extracting(r -> r.get("kind"))
        .containsExactlyInAnyOrder("patient.read", "patient.write");
    assertThat(rows).extracting(r -> r.get("patient_id")).containsOnly(id);
    assertThat(rows).extracting(r -> r.get("actor_id")).containsOnlyNulls();
    Map<String, Object> read =
        rows.stream().filter(r -> "patient.read".equals(r.get("kind"))).findFirst().orElseThrow();
    assertThat(read.get("method")).isEqualTo("GET");
    assertThat(read.get("path")).isEqualTo("/api/patients/" + id);
    assertThat(read.get("status")).isEqualTo("200");
    assertThat(read.get("ip")).isEqualTo("127.0.0.1");
    assertThat(read.get("user_agent")).isEqualTo("AuditIT/1.0");
    Map<String, Object> write =
        rows.stream().filter(r -> "patient.write".equals(r.get("kind"))).findFirst().orElseThrow();
    assertThat(write.get("method")).isEqualTo("PATCH");
  }

  @Test
  void entryReadIsJournaledWithItsEntryId() throws Exception {
    String patientId = createPatient();
    // Séance explicite : la séance d'accueil est créée de façon asynchrone après PatientCreated.
    send(post("/api/patients/" + patientId + "/sessions/start"), "{\"modality\":\"in_person\"}")
        .andExpect(status().isCreated());
    String entryId =
        JsonPath.read(
            send(
                    post("/api/patients/" + patientId + "/entries"),
                    "{\"type\":\"note\",\"body\":\"Premier échange\"}")
                .andDo(
                    r ->
                        assertThat(r.getResponse().getStatus())
                            .as(r.getResponse().getContentAsString())
                            .isEqualTo(201))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    send(get("/api/entries/" + entryId), null).andExpect(status().isOk());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForList(
                            "select kind from security_event where practitioner_id = ?"
                                + " and details->>'entryId' = ?",
                            String.class,
                            practitioner,
                            entryId))
                    .containsExactly("patient.read"));
    // Démarrer la séance et créer l'entrée (POST sous le patient) sont des écritures du patient.
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_event where practitioner_id = ?"
                    + " and kind = 'patient.write' and details->>'patientId' = ?",
                Integer.class,
                practitioner,
                patientId))
        .isEqualTo(2);
  }

  @Test
  void linkRoutesAreJournaledWithTheLinkIdOrNullWhenAbsent() throws Exception {
    UUID link = UUID.randomUUID();
    send(patch("/api/links/" + link), "{}");
    // /api/links sans identifiant : journalisé aussi, linkId nul.
    send(get("/api/links"), null);
    awaitRows(2);
    assertThat(
            jdbc.queryForList(
                "select details->>'linkId' from security_event"
                    + " where practitioner_id = ? and kind like 'patient.%' and jsonb_exists(details,"
                    + " 'linkId') order by kind",
                String.class, practitioner))
        // patient.read (GET sans id) puis patient.write (PATCH avec id).
        .containsExactly(null, link.toString());
  }

  @Test
  void unauthenticatedRequestsAreNotJournaled() throws Exception {
    long before =
        jdbc.queryForObject(
            "select count(*) from security_event where kind like 'patient.%'", Long.class);
    mvc.perform(get("/api/patients/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    // Laisse au pool le temps d'écrire une éventuelle ligne : il n'y en a pas.
    Thread.sleep(300);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_event where kind like 'patient.%'", Long.class))
        .isEqualTo(before);
  }

  @Test
  void aFailingInsertNeverChangesTheResponse() throws Exception {
    String id = createPatient();
    doThrow(new IllegalStateException("insertion impossible"))
        .when(writer)
        .write(any(AuditEvent.class));

    send(get("/api/patients/" + id), null).andExpect(status().isOk());

    verify(writer, timeout(5_000)).write(any(AuditEvent.class));
    Thread.sleep(200);
    assertThat(auditRows()).isZero();
  }
}
