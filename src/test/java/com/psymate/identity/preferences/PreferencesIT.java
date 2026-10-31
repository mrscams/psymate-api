package com.psymate.identity.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.identity.api.PreferencesChanged;
import java.util.List;
import java.util.UUID;
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
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** M1-12 : GET / PATCH /api/me/preferences (fiche 14). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PreferencesTestConfig.class, PreferencesIT.ListenerConfig.class})
class PreferencesIT {

  /** Un abonné transactionnel : c'est lui qui fait écrire la publication dans le registre. */
  static class ChangedListener {
    @ApplicationModuleListener
    void on(PreferencesChanged event) {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ListenerConfig {
    @Bean
    ChangedListener changedListener() {
      return new ChangedListener();
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PractitionerFacade facade;

  private UUID tenant;

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  private UUID insertPractitioner(String profession, String preferencesJson) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, preferences)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', ?, cast(? as jsonb))",
        id,
        tenant,
        email,
        email,
        profession,
        preferencesJson);
    return id;
  }

  private RequestPostProcessor as(UUID id, String profession) {
    CurrentPractitioner principal =
        new CurrentPractitioner(id, tenant, id + "@example.test", "Dr Test", profession, "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions patchJson(UUID id, String profession, String body) throws Exception {
    return mvc.perform(
        patch("/api/me/preferences")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(as(id, profession)));
  }

  private ResultActions getPrefs(UUID id, String profession) throws Exception {
    return mvc.perform(get("/api/me/preferences").with(as(id, profession)));
  }

  private String stored(UUID id) {
    return jdbc.queryForObject(
        "select preferences::text from practitioner where id = ?", String.class, id);
  }

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(get("/api/me/preferences")).andExpect(status().isUnauthorized());
  }

  @Test
  void freshAccountReadsAllNineDefaults() throws Exception {
    UUID id = insertPractitioner("psychologist", "{}");
    getPrefs(id, "psychologist")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(9))
        .andExpect(jsonPath("$['overview.view']").value("simple"))
        .andExpect(jsonPath("$['overview.window']").value("90d"))
        .andExpect(jsonPath("$['timeline.groupBy']").value("session"))
        .andExpect(jsonPath("$.theme").value("light"))
        .andExpect(jsonPath("$.locale").value("fr"))
        .andExpect(jsonPath("$['vocabulary.subjectLabel']").value("patient"))
        .andExpect(jsonPath("$['entryTypes.medicationVisible']").value(false))
        .andExpect(jsonPath("$['entries.defaultVisibility']").value("clinical"))
        .andExpect(jsonPath("$['sessions.defaultDurationMin']").value(60));
  }

  @Test
  void freshlyRegisteredCoachReadsClientAndNoMedication() throws Exception {
    String email = "coach" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
    String body =
        "{\"email\":\""
            + email
            + "\",\"password\":\"Zebre-Tournesol-42\",\"firstName\":\"Maya\","
            + "\"lastName\":\"Kaddour\",\"profession\":\"coach\","
            + "\"phone\":\"06 12 34 56 78\",\"address\":{\"street\":\"12 rue des Lilas\","
            + "\"postalCode\":\"69003\",\"city\":\"Lyon\"},\"remoteOnly\":false,"
            + "\"consents\":{\"terms\":true,\"privacy\":true}}";
    mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().is2xxSuccessful());
    UUID id =
        jdbc.queryForObject(
            "select id from practitioner where email_normalized = ?", UUID.class, email);
    getPrefs(id, "coach")
        .andExpect(jsonPath("$['vocabulary.subjectLabel']").value("client"))
        .andExpect(jsonPath("$['entryTypes.medicationVisible']").value(false))
        .andExpect(jsonPath("$['sessions.defaultDurationMin']").value(60))
        .andExpect(jsonPath("$.locale").value("fr"));
  }

  @Test
  void psychiatristDefaultsToMedicationVisible() throws Exception {
    UUID id = insertPractitioner("psychiatrist", "{}");
    getPrefs(id, "psychiatrist")
        .andExpect(jsonPath("$['entryTypes.medicationVisible']").value(true));
  }

  @Test
  void patchKeepsOtherKeys() throws Exception {
    UUID id = insertPractitioner("psychologist", "{\"locale\":\"en\"}");
    patchJson(id, "psychologist", "{\"overview.view\":\"complete\"}").andExpect(status().isOk());
    patchJson(id, "psychologist", "{\"theme\":\"dark\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.theme").value("dark"))
        .andExpect(jsonPath("$['overview.view']").value("complete"))
        .andExpect(jsonPath("$.locale").value("en"));
    getPrefs(id, "psychologist")
        .andExpect(jsonPath("$.theme").value("dark"))
        .andExpect(jsonPath("$['overview.view']").value("complete"));
    assertThat(stored(id)).contains("\"locale\"").contains("\"overview.view\"");
  }

  @Test
  void unknownKeyIsIgnoredAndNothingStored() throws Exception {
    UUID id = insertPractitioner("psychologist", "{}");
    patchJson(id, "psychologist", "{\"foo\":1}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.foo").doesNotExist())
        .andExpect(jsonPath("$.length()").value(9));
    assertThat(stored(id)).isEqualTo("{}");
  }

  @Test
  void outOfDomainValueIsRejectedWith422AndFaultyKey() throws Exception {
    UUID id = insertPractitioner("psychologist", "{}");
    patchJson(id, "psychologist", "{\"overview.window\":\"7d\",\"theme\":\"dark\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("overview.window"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    assertThat(stored(id)).isEqualTo("{}");
    patchJson(id, "psychologist", "{\"sessions.defaultDurationMin\":200}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("sessions.defaultDurationMin"));
  }

  @Test
  void nullRemovesKeyAndRestoresProfessionDefault() throws Exception {
    UUID id = insertPractitioner("coach", "{\"vocabulary.subjectLabel\":\"client\"}");
    patchJson(id, "coach", "{\"vocabulary.subjectLabel\":\"patient\"}")
        .andExpect(jsonPath("$['vocabulary.subjectLabel']").value("patient"));
    patchJson(id, "coach", "{\"vocabulary.subjectLabel\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$['vocabulary.subjectLabel']").value("client"));
    assertThat(stored(id)).isEqualTo("{}");
  }

  @Test
  void preferencesChangedIsPublishedWithChangedKeysOnly() throws Exception {
    UUID id = insertPractitioner("psychologist", "{\"theme\":\"dark\"}");
    patchJson(id, "psychologist", "{\"theme\":\"dark\",\"locale\":\"en\",\"foo\":1}")
        .andExpect(status().isOk());
    String serialized =
        jdbc.queryForObject(
            "select serialized_event from event_publication where event_type like ? and"
                + " serialized_event like ?",
            String.class,
            "%PreferencesChanged",
            "%" + id + "%");
    assertThat(serialized).contains("locale").doesNotContain("theme").doesNotContain("\"en\"");
    // Un PATCH sans changement ne publie rien.
    patchJson(id, "psychologist", "{\"foo\":2}").andExpect(status().isOk());
    Integer count =
        jdbc.queryForObject(
            "select count(*) from event_publication where event_type like ? and"
                + " serialized_event like ?",
            Integer.class,
            "%PreferencesChanged",
            "%" + id + "%");
    assertThat(count).isEqualTo(1);
  }

  @Test
  void facadeExposesEffectivePreferences() {
    UUID id = insertPractitioner("coach", "{\"theme\":\"soft\"}");
    assertThat(facade.preferencesOf(id))
        .containsEntry("theme", "soft")
        .containsEntry("vocabulary.subjectLabel", "client")
        .containsEntry("sessions.defaultDurationMin", 60)
        .hasSize(9);
    assertThat(facade.preferencesOf(UUID.randomUUID())).isEmpty();
  }

  @Test
  void concurrentPatchesOnDifferentKeysAreBothKept() throws Exception {
    UUID id = insertPractitioner("psychologist", "{}");
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var a =
          pool.submit(
              () -> {
                start.await();
                return patchJson(id, "psychologist", "{\"theme\":\"dark\"}").andReturn();
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return patchJson(id, "psychologist", "{\"locale\":\"en\"}").andReturn();
              });
      start.countDown();
      assertThat(a.get().getResponse().getStatus()).isEqualTo(200);
      assertThat(b.get().getResponse().getStatus()).isEqualTo(200);
    } finally {
      pool.shutdownNow();
    }
    getPrefs(id, "psychologist")
        .andExpect(jsonPath("$.theme").value("dark"))
        .andExpect(jsonPath("$.locale").value("en"));
  }

  @Test
  void otherPractitionerNeverReadsNorChangesMyPreferences() throws Exception {
    UUID alice = insertPractitioner("psychologist", "{\"theme\":\"dark\"}");
    // Praticien B dans un autre cabinet.
    UUID otherTenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Autre cabinet')", otherTenant);
    UUID bob = UUID.randomUUID();
    String email = bob + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'B', 'B', 'B', 'psychologist')",
        bob,
        otherTenant,
        email,
        email);
    CurrentPractitioner principal =
        new CurrentPractitioner(bob, otherTenant, email, "B", "psychologist", "none");
    RequestPostProcessor asBob =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    mvc.perform(get("/api/me/preferences").with(asBob))
        .andExpect(jsonPath("$.theme").value("light"));
    mvc.perform(
            patch("/api/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"soft\",\"practitionerId\":\"" + alice + "\"}")
                .with(asBob))
        .andExpect(status().isOk());
    assertThat(stored(alice)).isEqualTo("{\"theme\": \"dark\"}");
    assertThat(stored(bob)).contains("soft");
  }
}
