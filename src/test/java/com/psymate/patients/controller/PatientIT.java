package com.psymate.patients.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientArchived;
import com.psymate.patients.api.PatientCreated;
import com.psymate.patients.api.PatientUpdated;
import com.psymate.testsupport.SharedPostgres;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, PatientIT.ListenerConfig.class})
class PatientIT {

  /** Un abonné transactionnel : c'est lui qui fait écrire la publication dans le registre. */
  static class CreatedListener {
    @ApplicationModuleListener
    void on(PatientCreated event) {}

    @ApplicationModuleListener
    void on(PatientUpdated event) {}

    @ApplicationModuleListener
    void on(PatientArchived event) {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ListenerConfig {
    @Bean
    CreatedListener createdListener() {
      return new CreatedListener();
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private RequestPostProcessor auth;
  private UUID tenant;
  private UUID alice;
  private UUID bob;

  @BeforeEach
  void practitioners() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    alice = insertPractitioner();
    bob = insertPractitioner();
    as(alice);
  }

  private void as(UUID practitioner) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitioner,
            tenant,
            practitioner + "@example.test",
            "Dr Test",
            "psychologist",
            "none");
    auth =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private UUID insertPractitioner() {
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

  private ResultActions send(MockHttpServletRequestBuilder b, String body) throws Exception {
    return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(body).with(auth));
  }

  private String createPatient(String json) throws Exception {
    String res =
        send(post("/api/patients"), json)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return res.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
  }

  private String createPatientNamed(String name) throws Exception {
    return createPatient("{\"displayName\":\"" + name + "\"}");
  }

  // ---- création et lecture

  @Test
  void createWithOnlyDisplayNameThenRead() throws Exception {
    String id = createPatientNamed("Marie D.");
    mvc.perform(get("/api/patients/" + id).with(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Marie D."))
        .andExpect(jsonPath("$.currentVersion").value(1))
        .andExpect(jsonPath("$.archivedAt").doesNotExist())
        .andExpect(jsonPath("$.flags.length()").value(0));
  }

  @Test
  void createWithoutDisplayNameIs422() throws Exception {
    send(post("/api/patients"), "{\"firstName\":\"X\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("displayName"));
  }

  @Test
  void createStoresFullFiche() throws Exception {
    String id =
        createPatient(
            "{\"displayName\":\"Jo\",\"firstName\":\"Joëlle\",\"birthDate\":\"1990-04-02\","
                + "\"pronoun\":\"elle\",\"context\":{\"activity\":\"infirmière\"},"
                + "\"followUp\":{\"rhythm\":\"weekly\"},"
                + "\"flags\":[{\"kind\":\"risk\"}]}");
    mvc.perform(get("/api/patients/" + id).with(auth))
        .andExpect(jsonPath("$.birthDate").value("1990-04-02"))
        .andExpect(jsonPath("$.context.activity").value("infirmière"))
        .andExpect(jsonPath("$.followUp.rhythm").value("weekly"))
        .andExpect(jsonPath("$.flags[0].kind").value("risk"));
  }

  @Test
  void futureBirthDateIs422() throws Exception {
    send(post("/api/patients"), "{\"displayName\":\"A\",\"birthDate\":\"2999-01-01\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("birthDate"));
  }

  @Test
  void patientCreatedIsWrittenToEventPublication() throws Exception {
    String id = createPatientNamed("Evt");
    Integer n =
        jdbc.queryForObject(
            "select count(*) from event_publication where serialized_event like ? and listener_id like '%PatientIT%'",
            Integer.class, "%" + id + "%");
    assertThat(n).isEqualTo(1);
  }

  private int publications(String eventType, String patientId) {
    return jdbc.queryForObject(
        "select count(*) from event_publication where event_type like ? and serialized_event like ?"
            + " and listener_id like '%PatientIT%'",
        Integer.class, "%" + eventType, "%" + patientId + "%");
  }

  @Test
  void patchAndArchivePublishTheirEvents() throws Exception {
    String id = createPatientNamed("Events");
    send(patch("/api/patients/" + id), "{\"phone\":\"0102\"}").andExpect(status().isOk());
    mvc.perform(delete("/api/patients/" + id).with(auth)).andExpect(status().isNoContent());
    assertThat(publications("PatientCreated", id)).isEqualTo(1);
    assertThat(publications("PatientUpdated", id)).isEqualTo(1);
    assertThat(publications("PatientArchived", id)).isEqualTo(1);
  }

  @Test
  void failedCreationLeavesNoPatientAndNoEvent() throws Exception {
    send(post("/api/patients"), "{\"displayName\":\"Rollback\",\"tags\":[\"pasdetag\"]}")
        .andExpect(status().isUnprocessableEntity());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from patient where owner_id = ?", Integer.class, alice))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from event_publication where serialized_event like ?",
                Integer.class,
                "%" + alice + "%"))
        .isZero();
  }

  @Test
  void duplicateFlagKindsAtCreationAre409AndNothingIsInserted() throws Exception {
    send(
            post("/api/patients"),
            "{\"displayName\":\"Dup\",\"flags\":[{\"kind\":\"risk\"},{\"kind\":\"risk\"}]}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FLAG_ALREADY_ACTIVE"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from patient where owner_id = ?", Integer.class, alice))
        .isZero();
  }

  @Test
  void archivedPatientRejectsFlagsAndTagsButCanBeRestored() throws Exception {
    String id = createPatientNamed("Figée");
    mvc.perform(delete("/api/patients/" + id).with(auth)).andExpect(status().isNoContent());
    send(post("/api/patients/" + id + "/flags"), "{\"kind\":\"risk\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"));
    mvc.perform(delete("/api/patients/" + id + "/flags/risk").with(auth))
        .andExpect(status().isConflict());
    send(put("/api/patients/" + id + "/tags"), "{\"tags\":[]}").andExpect(status().isConflict());
    mvc.perform(post("/api/patients/" + id + "/restore").with(auth)).andExpect(status().isOk());
  }

  // ---- propriété

  @Test
  void anotherPractitionerGets403NotFoundIs404() throws Exception {
    String id = createPatientNamed("Privé");
    as(bob);
    mvc.perform(get("/api/patients/" + id).with(auth)).andExpect(status().isForbidden());
    send(patch("/api/patients/" + id), "{\"displayName\":\"Pirate\"}")
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/patients/" + id).with(auth)).andExpect(status().isForbidden());
    mvc.perform(get("/api/patients/" + id + "/versions").with(auth))
        .andExpect(status().isForbidden());
    send(post("/api/patients/" + id + "/flags"), "{\"kind\":\"risk\"}")
        .andExpect(status().isForbidden());
    send(put("/api/patients/" + id + "/tags"), "{\"tags\":[]}").andExpect(status().isForbidden());
    mvc.perform(post("/api/patients/" + id + "/restore").with(auth))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/patients").with(auth)).andExpect(jsonPath("$.items.length()").value(0));
    as(alice);
    mvc.perform(get("/api/patients/" + id).with(auth))
        .andExpect(jsonPath("$.displayName").value("Privé"));
    mvc.perform(get("/api/patients/" + UUID.randomUUID()).with(auth))
        .andExpect(status().isNotFound());
  }

  @Test
  void patientOfAnotherTenantIs403() throws Exception {
    UUID otherTenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Autre')", otherTenant);
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'X')",
        id,
        otherTenant,
        alice);
    mvc.perform(get("/api/patients/" + id).with(auth)).andExpect(status().isForbidden());
  }

  // ---- archivage

  @Test
  void archiveHidesFromDefaultListAndRestoreBringsBack() throws Exception {
    String id = createPatientNamed("Archivée");
    mvc.perform(delete("/api/patients/" + id).with(auth)).andExpect(status().isNoContent());

    mvc.perform(get("/api/patients").with(auth)).andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(get("/api/patients?archived=true").with(auth))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].displayName").value("Archivée"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from patient where id = ?::uuid and archived_at is not null",
                Integer.class,
                id))
        .isEqualTo(1);
    send(patch("/api/patients/" + id), "{\"phone\":\"1\"}").andExpect(status().isConflict());

    mvc.perform(post("/api/patients/" + id + "/restore").with(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").doesNotExist());
    mvc.perform(get("/api/patients").with(auth)).andExpect(jsonPath("$.items.length()").value(1));
  }

  // ---- versions

  @Test
  void threePatchesGiveThreeVersionsWithSnapshots() throws Exception {
    String id = createPatientNamed("Version");
    send(patch("/api/patients/" + id), "{\"followUp\":{\"rhythm\":\"weekly\"}}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentVersion").value(2));
    send(patch("/api/patients/" + id), "{\"followUp\":{\"rhythm\":\"monthly\"}}")
        .andExpect(status().isOk());
    send(patch("/api/patients/" + id), "{\"displayName\":\"Version B\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentVersion").value(4))
        .andExpect(jsonPath("$.followUp.rhythm").value("monthly"));

    mvc.perform(get("/api/patients/" + id + "/versions").with(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].snapshot.followUp.rhythm").value("weekly"))
        .andExpect(jsonPath("$[1].snapshot.followUp.rhythm").value("monthly"))
        .andExpect(jsonPath("$[2].snapshot.displayName").value("Version B"));
  }

  @Test
  void patchRejectsUnknownFieldAndEmptyName() throws Exception {
    String id = createPatientNamed("P");
    send(patch("/api/patients/" + id), "{\"authorType\":\"patient\"}")
        .andExpect(status().isUnprocessableEntity());
    send(patch("/api/patients/" + id), "{\"displayName\":\" \"}")
        .andExpect(status().isUnprocessableEntity());
  }

  // ---- drapeaux

  @Test
  void watchFlagNeedsReason() throws Exception {
    String id = createPatientNamed("Flag");
    send(post("/api/patients/" + id + "/flags"), "{\"kind\":\"watch\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("flag.reason"));
    send(post("/api/patients/" + id + "/flags"), "{\"kind\":\"watch\",\"reason\":\"sommeil\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.flags[0].reason").value("sommeil"));
  }

  @Test
  void duplicateActiveFlagIs409AndCanBeSetAgainAfterClearing() throws Exception {
    String id = createPatientNamed("Flag2");
    send(post("/api/patients/" + id + "/flags"), "{\"kind\":\"risk\"}")
        .andExpect(status().isCreated());
    send(post("/api/patients/" + id + "/flags"), "{\"kind\":\"risk\"}")
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/patients/" + id + "/flags/risk").with(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.flags.length()").value(0));
    mvc.perform(delete("/api/patients/" + id + "/flags/risk").with(auth))
        .andExpect(status().isNotFound());
    send(post("/api/patients/" + id + "/flags"), "{\"kind\":\"risk\"}")
        .andExpect(status().isCreated());
  }

  // ---- tags

  private void createTag(String label) throws Exception {
    send(post("/api/tags"), "{\"label\":\"" + label + "\"}").andExpect(status().isCreated());
  }

  @Test
  void tagsAreResolvedAndFilterable() throws Exception {
    createTag("travail");
    createTag("deuil");
    String id = createPatientNamed("Tagguée");
    createPatientNamed("Sans tag");
    send(put("/api/patients/" + id + "/tags"), "{\"tags\":[\"#Travail\",\"deuil\"]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tags.length()").value(2));
    mvc.perform(get("/api/patients?tag=travail").with(auth))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].displayName").value("Tagguée"));
    send(put("/api/patients/" + id + "/tags"), "{\"tags\":[\"deuil\"]}")
        .andExpect(jsonPath("$.tags.length()").value(1));
    mvc.perform(get("/api/patients?tag=travail").with(auth))
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void emotionLabelAsTagIs422TagIsEmotion() throws Exception {
    String id = createPatientNamed("Emo");
    send(put("/api/patients/" + id + "/tags"), "{\"tags\":[\"anxiété\"]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("TAG_IS_EMOTION"));
    send(post("/api/patients"), "{\"displayName\":\"E2\",\"tags\":[\"Anxiete\"]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("TAG_IS_EMOTION"));
  }

  @Test
  void unknownTagIs422() throws Exception {
    String id = createPatientNamed("Inconnu");
    send(put("/api/patients/" + id + "/tags"), "{\"tags\":[\"pasdetag\"]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("TAG_UNKNOWN"));
  }

  // ---- liste

  @Test
  void nameSearchIgnoresAccentsAndCaseAndSorts() throws Exception {
    createPatientNamed("Éloïse M.");
    createPatientNamed("Zoé");
    createPatientNamed("Alain");
    mvc.perform(get("/api/patients?q=eloise").with(auth))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].displayName").value("Éloïse M."));
    mvc.perform(get("/api/patients?q=ÉLOI").with(auth))
        .andExpect(jsonPath("$.items.length()").value(1));
    mvc.perform(get("/api/patients?q=%25").with(auth))
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(get("/api/patients?sort=name").with(auth))
        .andExpect(jsonPath("$.items[0].displayName").value("Alain"))
        .andExpect(jsonPath("$.items[1].displayName").value("Éloïse M."))
        .andExpect(jsonPath("$.items[2].displayName").value("Zoé"));
    mvc.perform(get("/api/patients?sort=nimportequoi").with(auth))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void listIsPaginatedByCursor() throws Exception {
    createPatientNamed("A1");
    createPatientNamed("A2");
    createPatientNamed("A3");
    String res =
        mvc.perform(get("/api/patients?limit=2").with(auth))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = res.replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");
    mvc.perform(get("/api/patients?limit=2&cursor=" + cursor).with(auth))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].displayName").value("A3"))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void unauthenticatedRequestIs401() throws Exception {
    mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
  }
}
