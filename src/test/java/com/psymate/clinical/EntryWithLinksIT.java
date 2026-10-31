package com.psymate.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.clinical.api.EntryCreated;
import com.psymate.linking.api.LinkAccepted;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Création d'une entrée avec links[] en une seule transaction (M2-02) : tout ou rien, validation
 * avant écriture, libellés localisés. Contexte complet, vraie base, jamais de mock.
 */
class EntryWithLinksIT extends ClinicalWebSupport {

  /**
   * Abonnés de test : le registre Modulith n'écrit une publication que s'il existe au moins un
   * abonné pour l'événement.
   */
  static class OutboxListeners {
    @ApplicationModuleListener
    void on(LinkAccepted event) {}

    @ApplicationModuleListener
    void on(EntryCreated event) {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ListenerConfig {
    @Bean
    OutboxListeners entryLinksOutboxListeners() {
      return new OutboxListeners();
    }
  }

  private String event;
  private String situation;

  @BeforeEach
  void entries() throws Exception {
    startSession(patient);
    event = createEntry(patient, "{\"type\":\"event\",\"title\":\"Dispute\"}");
    situation =
        createEntry(
            patient,
            """
            {"type":"situation","title":"Surcharge au travail",
             "occurred":{"from":"2026-03-15T10:00:00Z","precision":"month"}}
            """);
  }

  // ---- helpers

  private static String feeling(String links) {
    return "{\"type\":\"feeling\",\"feeling\":{\"emotionCode\":\"anxiety\",\"intensity\":6}"
        + (links == null ? "" : ",\"links\":" + links)
        + "}";
  }

  private static String link(String targetId, String linkType, String direction) {
    return "{\"targetId\":\""
        + targetId
        + "\",\"linkType\":\""
        + linkType
        + "\""
        + (direction == null ? "" : ",\"direction\":\"" + direction + "\"")
        + "}";
  }

  private int entryRows() {
    return count("select count(*) from entry where patient_id = ?", patient);
  }

  private int linkRows() {
    return count("select count(*) from link where patient_id = ?", patient);
  }

  private int linkCount(String entryId) {
    return count("select link_count from entry where id = ?::uuid", entryId);
  }

  private int publications(Class<?> type, String entryId) {
    return count(
        "select count(*) from event_publication where event_type = ? and listener_id like '%OutboxListeners%'"
            + " and serialized_event::text like ?",
        type.getName(), "%" + entryId + "%");
  }

  /** POST d'un ressenti avec ces liens : 422, et ni entrée ni lien n'ont été écrits. */
  private ResultActions refused(String links) throws Exception {
    int entriesBefore = entryRows();
    int linksBefore = linkRows();
    ResultActions result = post(entriesUrl(patient), feeling(links));
    result.andExpect(status().isUnprocessableEntity());
    assertThat(entryRows()).as("aucune entrée créée").isEqualTo(entriesBefore);
    assertThat(linkRows()).as("aucun lien créé").isEqualTo(linksBefore);
    return result;
  }

  private ResultActions postWithLanguage(String language, String json) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post(entriesUrl(patient))
            .header("Accept-Language", language)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
            .with(me));
  }

  // ---- succès

  @Test
  void aFeelingWithTwoLinksIsCreatedInOneTransaction() throws Exception {
    ResultActions result =
        post(
                entriesUrl(patient),
                feeling(
                    "["
                        + link(event, "triggers", "incoming")
                        + ","
                        + link(situation, "co_occurs", null)
                        + "]"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("feeling"))
            .andExpect(jsonPath("$.linkCount").value(2))
            .andExpect(jsonPath("$.links.length()").value(2))
            .andExpect(jsonPath("$.links[0].id").isNotEmpty())
            .andExpect(jsonPath("$.links[0].sourceId").value(event))
            .andExpect(jsonPath("$.links[0].linkType").value("triggers"))
            .andExpect(jsonPath("$.links[0].label").value("déclenché par"))
            .andExpect(jsonPath("$.links[1].targetId").value(situation))
            .andExpect(jsonPath("$.links[1].linkType").value("co_occurs"))
            .andExpect(jsonPath("$.links[1].label").value("survient avec"));
    String id = idOf(result);
    String json = body(result);
    assertThat(JsonPath.<String>read(json, "$.links[0].targetId")).isEqualTo(id);
    assertThat(JsonPath.<String>read(json, "$.links[1].sourceId")).isEqualTo(id);

    assertThat(
            count(
                "select count(*) from link where patient_id = ? and provenance = 'manual'"
                    + " and status = 'accepted' and (source_id = ?::uuid or target_id = ?::uuid)",
                patient,
                id,
                id))
        .isEqualTo(2);
    assertThat(linkCount(id)).isEqualTo(2);
    assertThat(linkCount(event)).isEqualTo(1);
    assertThat(linkCount(situation)).isEqualTo(1);
    assertThat(publications(LinkAccepted.class, id)).isEqualTo(2);
    assertThat(publications(EntryCreated.class, id)).isEqualTo(1);

    // links n'est dans la réponse que de la création ; les liens se relisent par M2-01.
    get("/api/entries/" + id).andExpect(jsonPath("$.links").doesNotExist());
    get("/api/entries/" + id + "/links").andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void outgoingMakesTheNewEntryTheSource() throws Exception {
    ResultActions result =
        post(entriesUrl(patient), feeling("[" + link(situation, "co_occurs", "outgoing") + "]"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.links[0].targetId").value(situation));
    assertThat(JsonPath.<String>read(body(result), "$.links[0].sourceId")).isEqualTo(idOf(result));
  }

  @Test
  void aMissingOrEmptyLinksListCreatesTheEntryAsBefore() throws Exception {
    post(entriesUrl(patient), feeling(null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links.length()").value(0))
        .andExpect(jsonPath("$.linkCount").value(0));
    String id =
        idOf(
            post(entriesUrl(patient), feeling("[]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.links.length()").value(0))
                .andExpect(jsonPath("$.linkCount").value(0)));
    assertThat(linkCount(id)).isZero();
    assertThat(linkRows()).isZero();
  }

  @Test
  void theLabelIsEnglishWithAcceptLanguageEn() throws Exception {
    postWithLanguage("en", feeling("[" + link(event, "triggers", "incoming") + "]"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links[0].label").value("triggered by"));
    postWithLanguage(
            "en",
            "{\"type\":\"event\",\"title\":\"x\",\"links\":["
                + link(situation, "triggers", null)
                + "]}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links[0].label").value("triggers"));
  }

  // ---- refus métier : rollback complet

  @Test
  void aLinkToAnotherPatientsEntryOfTheSamePractitionerRollsEverythingBack() throws Exception {
    UUID other = fx.newPatient(LocalDate.of(1985, 1, 1));
    startSession(other);
    String foreign = createEntry(other, "{\"type\":\"event\",\"title\":\"Ailleurs\"}");

    refused(
            "["
                + link(situation, "co_occurs", null)
                + ","
                + link(foreign, "triggers", "incoming")
                + "]")
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("links[1].targetId"))
        .andExpect(jsonPath("$.errors[0].code").value("DIFFERENT_PATIENT"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    assertThat(linkCount(situation)).as("le compteur de la cible n'a pas bougé").isZero();
  }

  @Test
  void aLinkTypeNotAllowedForThePairIsRefusedWithTheAllowedTypes() throws Exception {
    // triggers ne part pas d'un ressenti : feeling -> event est interdit.
    refused("[" + link(event, "triggers", "outgoing") + "]")
        .andExpect(jsonPath("$.errors[0].field").value("links[0].linkType"))
        .andExpect(jsonPath("$.errors[0].code").value("LINK_TYPE_NOT_ALLOWED"))
        .andExpect(jsonPath("$.allowedLinkTypes").isArray())
        .andExpect(jsonPath("$.allowedLinkTypes", hasItems("caused_by", "co_occurs")));
  }

  @Test
  void anUnknownLinkTypeIsRefused() throws Exception {
    refused("[" + link(event, "inventé", "incoming") + "]")
        .andExpect(jsonPath("$.errors[0].field").value("links[0].linkType"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_LINK_TYPE"));
  }

  @Test
  void aDuplicateInsideLinksIsRefusedAtTheSecondElement() throws Exception {
    refused(
            "["
                + link(event, "triggers", "incoming")
                + ","
                + link(event, "triggers", "incoming")
                + "]")
        .andExpect(jsonPath("$.errors[0].field").value("links[1].targetId"))
        .andExpect(jsonPath("$.errors[0].code").value("LINK_EXISTS"));
    // Un type non dirigé posé dans les deux sens est aussi un doublon.
    refused(
            "["
                + link(situation, "co_occurs", "outgoing")
                + ","
                + link(situation, "co_occurs", "incoming")
                + "]")
        .andExpect(jsonPath("$.errors[0].field").value("links[1].targetId"))
        .andExpect(jsonPath("$.errors[0].code").value("LINK_EXISTS"));
  }

  @Test
  void anUnknownTargetIsRefused() throws Exception {
    refused("[" + link(UUID.randomUUID().toString(), "co_occurs", null) + "]")
        .andExpect(jsonPath("$.errors[0].field").value("links[0].targetId"))
        .andExpect(jsonPath("$.errors[0].code").value("ENTRY_NOT_FOUND"));
  }

  @Test
  void anArchivedTargetIsRefused() throws Exception {
    String archived = createEntry(patient, "{\"type\":\"event\",\"title\":\"Archivé\"}");
    delete("/api/entries/" + archived).andExpect(status().isNoContent());
    refused("[" + link(archived, "triggers", "incoming") + "]")
        .andExpect(jsonPath("$.errors[0].field").value("links[0].targetId"))
        .andExpect(jsonPath("$.errors[0].code").value("ENTRY_ARCHIVED"));
  }

  @Test
  void aTargetOfAnotherPractitionersPatientIsForbiddenAndNothingIsCreated() throws Exception {
    UUID otherPractitioner = fx.newPractitioner();
    UUID foreignPatient = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name, birth_date)"
            + " values (?, ?, ?, 'Patient autre', ?)",
        foreignPatient,
        fx.tenant,
        otherPractitioner,
        LocalDate.of(1980, 1, 1));
    RequestPostProcessor other = as(otherPractitioner);
    send(
            MockMvcRequestBuilders.post("/api/patients/" + foreignPatient + "/sessions/start"),
            "{\"modality\":\"in_person\"}",
            other)
        .andExpect(status().isCreated());
    String foreign =
        idOf(
            send(
                    MockMvcRequestBuilders.post(entriesUrl(foreignPatient)),
                    "{\"type\":\"event\",\"title\":\"Secret\"}",
                    other)
                .andExpect(status().isCreated()));

    int entriesBefore = entryRows();
    post(entriesUrl(patient), feeling("[" + link(foreign, "triggers", "incoming") + "]"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    assertThat(entryRows()).isEqualTo(entriesBefore);
    assertThat(linkRows()).isZero();
  }

  // ---- validation avant écriture

  @Test
  void aMalformedLinkReportsEveryFieldBeforeAnyWrite() throws Exception {
    int entriesBefore = entryRows();
    post(entriesUrl(patient), feeling("[{\"linkType\":\" \",\"direction\":\"sideways\"}]"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(
            jsonPath(
                "$.errors[*].field",
                hasItems("links[0].targetId", "links[0].linkType", "links[0].direction")))
        .andExpect(
            jsonPath("$.errors[?(@.field == 'links[0].targetId')].code").value(hasItem("REQUIRED")))
        .andExpect(
            jsonPath("$.errors[?(@.field == 'links[0].linkType')].code").value(hasItem("REQUIRED")))
        .andExpect(
            jsonPath("$.errors[?(@.field == 'links[0].direction')].code")
                .value(hasItem("INVALID_VALUE")))
        .andExpect(jsonPath("$.errors[?(@.field == 'links[0].direction')].message").isNotEmpty());
    assertThat(entryRows()).isEqualTo(entriesBefore);
    assertThat(linkRows()).isZero();
  }

  @Test
  void aSessionIsNotLinkedAtCreation() throws Exception {
    post(
            entriesUrl(patient),
            "{\"type\":\"session\",\"session\":{\"modality\":\"in_person\"},\"links\":["
                + link(event, "triggers", null)
                + "]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("links"))
        .andExpect(jsonPath("$.errors[0].code").value("NOT_ALLOWED_ON_CREATE"));
  }

  @Test
  void linksAreRefusedOnPatch() throws Exception {
    String id = createEntry(patient, feeling(null));
    patch("/api/entries/" + id, "{\"links\":[" + link(event, "triggers", "incoming") + "]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("links"))
        .andExpect(jsonPath("$.errors[0].code").value("NOT_SUPPORTED_ON_PATCH"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("/links")));
    assertThat(linkRows()).isZero();
  }
}
