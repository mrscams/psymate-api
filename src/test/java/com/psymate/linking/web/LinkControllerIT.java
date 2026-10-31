package com.psymate.linking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.PostgresTestConfig;
import com.psymate.clinical.application.EntryService;
import com.psymate.clinical.domain.EntryType;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.linking.LinkingFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Endpoints liens (M2-01) : poser, changer le type, supprimer, lister, avec la phrase lisible, les
 * erreurs problem+json et la propriété. PostgreSQL réel (Testcontainers), jamais de mock de base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class LinkControllerIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntryService entries;

  private LinkingFixtures fx;
  private RequestPostProcessor me;
  private UUID patient;
  private UUID session;
  private UUID event;
  private UUID feeling;
  private UUID note;

  @BeforeEach
  void setUp() {
    fx = new LinkingFixtures(jdbc, entries);
    patient = fx.newPatient();
    session = fx.session(patient);
    event = fx.entry(patient, session, EntryType.EVENT);
    feeling = fx.entry(patient, session, EntryType.FEELING);
    note = fx.entry(patient, session, EntryType.NOTE);
    // Les requêtes portent leur propre principal ; rien ne doit traîner sur le thread de test.
    SecurityContextHolder.clearContext();
    me = as(fx.practitioner);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  private RequestPostProcessor as(UUID practitionerId) {
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

  private ResultActions send(
      MockHttpServletRequestBuilder builder, String body, RequestPostProcessor auth, String lang)
      throws Exception {
    if (body != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.getBytes(StandardCharsets.UTF_8));
    }
    if (lang != null) {
      builder.header("Accept-Language", lang);
    }
    return mvc.perform(builder.with(auth));
  }

  private ResultActions post(String url, String body) throws Exception {
    return send(MockMvcRequestBuilders.post(url), body, me, null);
  }

  private ResultActions patch(String url, String body) throws Exception {
    return send(MockMvcRequestBuilders.patch(url), body, me, null);
  }

  private ResultActions get(String url) throws Exception {
    return send(MockMvcRequestBuilders.get(url), null, me, null);
  }

  private ResultActions delete(String url) throws Exception {
    return send(MockMvcRequestBuilders.delete(url), null, me, null);
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private static String idOf(ResultActions result) throws Exception {
    return JsonPath.read(body(result), "$.id");
  }

  private static String links(UUID entryId) {
    return "/api/entries/" + entryId + "/links";
  }

  private static String request(UUID targetId, String linkType, String direction) {
    return "{\"targetId\":\"%s\",\"linkType\":\"%s\"%s}"
        .formatted(
            targetId, linkType, direction == null ? "" : ",\"direction\":\"" + direction + "\"");
  }

  /** Pose event triggers feeling par l'API et renvoie l'identifiant du lien. */
  private String postTriggers() throws Exception {
    return idOf(
        post(links(event), request(feeling, "triggers", "outgoing"))
            .andExpect(status().isCreated()));
  }

  private String linkStatus(String linkId) {
    return jdbc.queryForObject("select status from link where id = ?::uuid", String.class, linkId);
  }

  // ---- POST : création

  @Test
  void outgoingLinkHasTheOpenEntryAsSource() throws Exception {
    post(links(event), request(feeling, "triggers", "outgoing"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", containsString("/api/links/")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.sourceId").value(event.toString()))
        .andExpect(jsonPath("$.targetId").value(feeling.toString()))
        .andExpect(jsonPath("$.linkType").value("triggers"))
        .andExpect(jsonPath("$.label").value("déclenche"))
        .andExpect(jsonPath("$.sentence").value("Décès du père déclenche Peur au réveil"))
        .andExpect(jsonPath("$.provenance").value("manual"))
        .andExpect(jsonPath("$.status").value("accepted"))
        .andExpect(jsonPath("$.rationale").value(nullValue()))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.authorId").value(fx.practitioner.toString()));
  }

  @Test
  void incomingLinkHasTheOpenEntryAsTargetAndReadsWithTheInverseLabel() throws Exception {
    post(links(feeling), request(event, "triggers", "incoming"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sourceId").value(event.toString()))
        .andExpect(jsonPath("$.targetId").value(feeling.toString()))
        .andExpect(jsonPath("$.linkType").value("triggers"))
        .andExpect(jsonPath("$.label").value("déclenché par"))
        .andExpect(jsonPath("$.sentence").value("Peur au réveil déclenché par Décès du père"));
    assertThat(
            fx.count(
                "select count(*) from link where source_id = ? and target_id = ?", event, feeling))
        .isEqualTo(1);
  }

  @Test
  void directionDefaultsToOutgoingAndRationaleIsKept() throws Exception {
    post(
            links(event),
            "{\"targetId\":\"%s\",\"linkType\":\"triggers\",\"rationale\":\"depuis le décès\"}"
                .formatted(feeling))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sourceId").value(event.toString()))
        .andExpect(jsonPath("$.rationale").value("depuis le décès"));
  }

  @Test
  void creationUpdatesTheEntryLinkCountReadThroughTheEntryEndpoint() throws Exception {
    postTriggers();

    get("/api/entries/" + event).andExpect(jsonPath("$.linkCount").value(1));
    get("/api/entries/" + feeling).andExpect(jsonPath("$.linkCount").value(1));
    get("/api/entries/" + note).andExpect(jsonPath("$.linkCount").value(0));
  }

  @Test
  void aLinkedEntryCorrectedInsideTheGraceWindowGetsANewVersion() throws Exception {
    postTriggers();

    patch(
            "/api/entries/" + event,
            "{\"title\":\"Décès du père (précisé)\",\"reason\":\"précision\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.linkCount").value(1));
  }

  // ---- POST : 422

  @Test
  void noteToFeelingTriggersIsRefusedWithTheAllowedTypes() throws Exception {
    post(links(note), request(feeling, "triggers", "outgoing"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.code").value("LINK_TYPE_NOT_ALLOWED"))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].field").value("linkType"))
        .andExpect(jsonPath("$.errors[0].code").value("LINK_TYPE_NOT_ALLOWED"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("survient avec")))
        .andExpect(jsonPath("$.allowedLinkTypes", hasSize(2)))
        .andExpect(jsonPath("$.allowedLinkTypes[0].code").value("co_occurs"))
        .andExpect(jsonPath("$.allowedLinkTypes[0].label").value("survient avec"))
        .andExpect(jsonPath("$.allowedLinkTypes[1].code").value("follows"))
        .andExpect(jsonPath("$.allowedLinkTypes[1].label").value("fait suite à"));
    assertThat(fx.count("select count(*) from link where patient_id = ?", patient)).isZero();
  }

  @Test
  void aTypeThatAllowsNothingForThePairSaysSo() throws Exception {
    fx.authenticate(fx.practitioner);
    UUID other = fx.session(patient);
    SecurityContextHolder.clearContext();

    post(links(session), request(other, "triggers", "outgoing"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("LINK_TYPE_NOT_ALLOWED"))
        .andExpect(jsonPath("$.allowedLinkTypes", hasSize(0)))
        .andExpect(jsonPath("$.detail").value(containsString("Aucun type de lien")));
  }

  @Test
  void selfLinkIsRefused() throws Exception {
    post(links(event), request(event, "follows", "outgoing"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("SELF_LINK"))
        .andExpect(jsonPath("$.errors[0].field").value("targetId"))
        .andExpect(jsonPath("$.errors[0].code").value("SELF_LINK"));
  }

  @Test
  void unknownLinkTypeIsRefused() throws Exception {
    post(links(event), request(feeling, "causes", "outgoing"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("UNKNOWN_LINK_TYPE"))
        .andExpect(jsonPath("$.errors[0].field").value("linkType"));
  }

  @Test
  void entriesOfTwoPatientsAreRefused() throws Exception {
    fx.authenticate(fx.practitioner);
    UUID otherPatient = fx.newPatient();
    UUID otherEntry = fx.entry(otherPatient, fx.session(otherPatient), EntryType.FEELING);
    SecurityContextHolder.clearContext();

    post(links(event), request(otherEntry, "triggers", "outgoing"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DIFFERENT_PATIENT"))
        .andExpect(jsonPath("$.errors[0].field").value("targetId"));
  }

  @Test
  void missingFieldsGiveOneErrorPerField() throws Exception {
    post(links(event), "{}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[?(@.field=='targetId')].code").value("REQUIRED"))
        .andExpect(jsonPath("$.errors[?(@.field=='linkType')].code").value("REQUIRED"))
        .andExpect(
            jsonPath("$.errors[?(@.field=='linkType')].message")
                .value("Le type de lien est obligatoire."));
  }

  @Test
  void invalidDirectionRationaleAndIdentifierAreFieldErrors() throws Exception {
    post(links(event), request(feeling, "triggers", "sideways"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("direction"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));

    post(
            links(event),
            "{\"targetId\":\"%s\",\"linkType\":\"triggers\",\"rationale\":\"%s\"}"
                .formatted(feeling, "x".repeat(501)))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("rationale"))
        .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));

    post(links(event), "{\"targetId\":\"pas-un-uuid\",\"linkType\":\"triggers\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("targetId"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

    post(links(event), "{pas du json")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void messagesFollowAcceptLanguage() throws Exception {
    send(
            MockMvcRequestBuilders.post(links(note)),
            request(feeling, "triggers", "outgoing"),
            me,
            "en")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value(containsString("Possible types: co-occurs with")))
        .andExpect(jsonPath("$.allowedLinkTypes[1].label").value("follows"));
    String linkId = postTriggers();
    send(MockMvcRequestBuilders.get(links(feeling)), null, me, "en")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(linkId))
        .andExpect(jsonPath("$[0].label").value("triggered by"))
        .andExpect(jsonPath("$[0].sentence").value("Peur au réveil triggered by Décès du père"));
  }

  @Test
  void linkingAnArchivedEntryIsAConflict() throws Exception {
    fx.authenticate(fx.practitioner);
    UUID archived = fx.entry(patient, session, EntryType.EVENT);
    entries.archive(archived);
    SecurityContextHolder.clearContext();

    post(links(event), request(archived, "follows", "outgoing"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("ENTRY_ARCHIVED"))
        .andExpect(jsonPath("$.errors[0].field").value("targetId"))
        .andExpect(jsonPath("$.errors[0].code").value("ENTRY_ARCHIVED"))
        .andExpect(
            jsonPath("$.detail").value("Cette entrée est archivée : on ne peut pas la lier."));
    post(links(archived), request(feeling, "triggers", "outgoing"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].field").value("sourceId"));
    assertThat(fx.count("select count(*) from link where patient_id = ?", patient)).isZero();
  }

  // ---- POST : 409, 404, 403

  @Test
  void duplicateIsAConflictThatNamesTheExistingLink() throws Exception {
    String existing = postTriggers();

    post(links(event), request(feeling, "triggers", "outgoing"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LINK_EXISTS"))
        .andExpect(jsonPath("$.existingLinkId").value(existing))
        .andExpect(jsonPath("$.detail").value("Ce lien existe déjà."));
    // Le même lien posé depuis l'autre entrée (direction incoming) est le même doublon.
    post(links(feeling), request(event, "triggers", "incoming"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.existingLinkId").value(existing));
  }

  @Test
  void unknownEntryIsNotFound() throws Exception {
    post(links(UUID.randomUUID()), request(feeling, "triggers", "outgoing"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("ENTRY_NOT_FOUND"));
    post(links(event), request(UUID.randomUUID(), "triggers", "outgoing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENTRY_NOT_FOUND"));
    get(links(UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void anEntryOfAnotherPractitionersPatientIsForbidden() throws Exception {
    UUID strangerId = fx.newPractitioner();
    fx.authenticate(strangerId);
    UUID strangerPatient = fx.newPatient();
    UUID strangerEntry = fx.entry(strangerPatient, fx.session(strangerPatient), EntryType.FEELING);
    SecurityContextHolder.clearContext();
    RequestPostProcessor stranger = as(strangerId);

    // Source étrangère (l'entrée de l'URL), cible étrangère, puis lecture d'une entrée étrangère.
    post(links(strangerEntry), request(event, "triggers", "incoming"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    post(links(event), request(strangerEntry, "triggers", "outgoing"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    send(
            MockMvcRequestBuilders.post(links(event)),
            request(feeling, "triggers", "outgoing"),
            stranger,
            null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    send(MockMvcRequestBuilders.get(links(event)), null, stranger, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    get(links(strangerEntry))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    assertThat(fx.count("select count(*) from link where patient_id = ?", patient)).isZero();
    assertThat(fx.linkCount(strangerEntry)).isZero();
  }

  @Test
  void requestsWithoutASessionAreUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get(links(event)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  // ---- GET

  @Test
  void listShowsAcceptedLinksOfBothSidesWithTheRightVerb() throws Exception {
    String triggers = postTriggers();
    UUID coOccurs = note;
    String symmetric =
        idOf(
            post(links(feeling), request(coOccurs, "co_occurs", "incoming"))
                .andExpect(status().isCreated()));

    get(links(feeling))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].id").value(triggers))
        .andExpect(jsonPath("$[0].label").value("déclenché par"))
        .andExpect(jsonPath("$[0].sentence").value("Peur au réveil déclenché par Décès du père"))
        .andExpect(jsonPath("$[1].id").value(symmetric))
        .andExpect(jsonPath("$[1].label").value("survient avec"))
        .andExpect(jsonPath("$[1].sentence").value("Peur au réveil survient avec Une note"));
    get(links(event))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].label").value("déclenche"))
        .andExpect(jsonPath("$[0].sentence").value("Décès du père déclenche Peur au réveil"));
    get(links(UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void listIsEmptyForAnEntryWithoutLinks() throws Exception {
    get(links(note)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
  }

  // ---- PATCH

  @Test
  void changingTheTypeReturnsTheNewLinkAndSupersedesTheOldOne() throws Exception {
    String old = postTriggers();

    patch("/api/links/" + old, "{\"linkType\":\"worsens\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(not(old)))
        .andExpect(jsonPath("$.linkType").value("worsens"))
        .andExpect(jsonPath("$.label").value("aggrave"))
        .andExpect(jsonPath("$.sentence").value("Décès du père aggrave Peur au réveil"))
        .andExpect(jsonPath("$.provenance").value("manual"))
        .andExpect(jsonPath("$.status").value("accepted"));

    assertThat(linkStatus(old)).isEqualTo("superseded");
    get(links(event))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].linkType").value("worsens"));
    get("/api/entries/" + event).andExpect(jsonPath("$.linkCount").value(1));
  }

  @Test
  void changingTheTypeHonoursTheMatrixAndTheConflictRules() throws Exception {
    String old = postTriggers();

    patch("/api/links/" + old, "{\"linkType\":\"treats\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("LINK_TYPE_NOT_ALLOWED"))
        .andExpect(jsonPath("$.allowedLinkTypes[?(@.code=='triggers')]").exists());
    patch("/api/links/" + old, "{\"linkType\":\"triggers\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LINK_EXISTS"))
        .andExpect(jsonPath("$.existingLinkId").value(old));
    patch("/api/links/" + old, "{}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("linkType"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    assertThat(linkStatus(old)).isEqualTo("accepted");
  }

  @Test
  void aSupersededLinkCannotBeChanged() throws Exception {
    String old = postTriggers();
    patch("/api/links/" + old, "{\"linkType\":\"worsens\"}").andExpect(status().isOk());

    patch("/api/links/" + old, "{\"linkType\":\"relieves\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LINK_NOT_ACTIVE"));
  }

  // ---- DELETE

  @Test
  void deletingALinkReturnsNoContentAndKeepsTheRow() throws Exception {
    String id = postTriggers();

    delete("/api/links/" + id).andExpect(status().isNoContent());

    assertThat(linkStatus(id)).isEqualTo("superseded");
    get(links(event)).andExpect(jsonPath("$", hasSize(0)));
    get("/api/entries/" + event).andExpect(jsonPath("$.linkCount").value(0));
    // Idempotent : un second DELETE ne casse rien.
    delete("/api/links/" + id).andExpect(status().isNoContent());
  }

  // ---- 404 et 403 sur un lien

  @Test
  void unknownLinkIsNotFound() throws Exception {
    String unknown = UUID.randomUUID().toString();
    patch("/api/links/" + unknown, "{\"linkType\":\"worsens\"}")
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
    delete("/api/links/" + unknown)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
  }

  @Test
  void aLinkOfAnotherPractitionerIsForbidden() throws Exception {
    String id = postTriggers();
    RequestPostProcessor stranger = as(fx.newPractitioner());

    send(
            MockMvcRequestBuilders.patch("/api/links/" + id),
            "{\"linkType\":\"worsens\"}",
            stranger,
            null)
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    send(MockMvcRequestBuilders.delete("/api/links/" + id), null, stranger, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    assertThat(linkStatus(id)).isEqualTo("accepted");
  }

  @Test
  void theLocationHeaderPointsAtTheLink() throws Exception {
    ResultActions created = post(links(event), request(feeling, "triggers", "outgoing"));

    created
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", endsWith("/api/links/" + idOf(created))));
  }
}
