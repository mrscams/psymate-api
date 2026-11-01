package com.psymate.linking.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.PostgresTestConfig;
import com.psymate.clinical.application.EntryService;
import com.psymate.clinical.domain.EntryType;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.linking.LinkingFixtures;
import com.psymate.linking.application.LinkService;
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
 * Endpoints du graphe (M2-03) : voisinage et expand, forme JSON, erreurs problem+json par champ en
 * français puis en anglais, propriété du patient. PostgreSQL réel (Testcontainers).
 *
 * <p>Graphe : event triggers feeling1, event triggers feeling2, note co_occurs event.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class GraphControllerIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntryService entries;
  @Autowired private LinkService links;

  private LinkingFixtures fx;
  private RequestPostProcessor me;
  private UUID patient;
  private UUID session;
  private UUID event;
  private UUID feeling1;
  private UUID feeling2;
  private UUID note;

  @BeforeEach
  void setUp() {
    fx = new LinkingFixtures(jdbc, entries);
    patient = fx.newPatient();
    session = fx.session(patient);
    event = fx.entry(patient, session, EntryType.EVENT);
    feeling1 = fx.entry(patient, session, EntryType.FEELING);
    feeling2 = fx.entry(patient, session, EntryType.FEELING);
    note = fx.entry(patient, session, EntryType.NOTE);
    links.create(event, feeling1, "triggers", null);
    links.create(event, feeling2, "triggers", null);
    links.create(note, event, "co_occurs", null);
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

  private ResultActions get(String query) throws Exception {
    return send(MockMvcRequestBuilders.get(graph(patient) + query), null, me, null);
  }

  private ResultActions get(String query, String lang) throws Exception {
    return send(MockMvcRequestBuilders.get(graph(patient) + query), null, me, lang);
  }

  private ResultActions expand(String body) throws Exception {
    return send(MockMvcRequestBuilders.post(graph(patient) + "/expand"), body, me, null);
  }

  private static String graph(UUID patientId) {
    return "/api/patients/" + patientId + "/graph";
  }

  // ---- GET : voisinage

  @Test
  void neighbourhoodHasTheDocumentedShape() throws Exception {
    get("?root=" + event + "&depth=1")
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.nodes", hasSize(4)))
        .andExpect(jsonPath("$.edges", hasSize(3)))
        .andExpect(jsonPath("$.superNodes", hasSize(0)))
        .andExpect(jsonPath("$.truncated").value(false))
        .andExpect(jsonPath("$.nodes[?(@.id == '%s')].type".formatted(event), contains("event")))
        .andExpect(
            jsonPath("$.nodes[?(@.id == '%s')].title".formatted(event), contains("Décès du père")))
        .andExpect(jsonPath("$.nodes[?(@.id == '%s')].distance".formatted(event), contains(0)))
        .andExpect(jsonPath("$.nodes[?(@.id == '%s')].degree".formatted(event), contains(3)))
        .andExpect(
            jsonPath(
                "$.nodes[?(@.id == '%s')].authorType".formatted(event), contains("practitioner")))
        .andExpect(jsonPath("$.nodes[?(@.id == '%s')].occurred".formatted(event), hasSize(1)))
        .andExpect(
            jsonPath("$.nodes[?(@.id == '%s')].type".formatted(feeling1), contains("feeling")))
        .andExpect(
            jsonPath("$.nodes[?(@.id == '%s')].emotion".formatted(feeling1), contains("anxiety")))
        .andExpect(jsonPath("$.nodes[?(@.id == '%s')].intensity".formatted(feeling1), contains(7)))
        .andExpect(jsonPath("$.nodes[?(@.id == '%s')].distance".formatted(feeling1), contains(1)))
        .andExpect(jsonPath("$.nodes[?(@.id == '%s')].degree".formatted(feeling1), contains(1)))
        .andExpect(
            jsonPath(
                "$.edges[?(@.target == '%s')].source".formatted(feeling1),
                contains(event.toString())))
        .andExpect(
            jsonPath(
                "$.edges[?(@.target == '%s')].linkType".formatted(feeling1), contains("triggers")))
        .andExpect(
            jsonPath("$.edges[?(@.target == '%s')].directed".formatted(feeling1), contains(true)))
        .andExpect(
            jsonPath(
                "$.edges[?(@.target == '%s')].provenance".formatted(feeling1), contains("manual")))
        .andExpect(
            jsonPath(
                "$.edges[?(@.target == '%s')].status".formatted(feeling1), contains("accepted")))
        .andExpect(jsonPath("$.edges[0].id").exists());
  }

  @Test
  void depthDefaultsToTwo() throws Exception {
    // depuis feeling1 : event à 1, feeling2 et note à 2
    get("?root=" + feeling1).andExpect(status().isOk()).andExpect(jsonPath("$.nodes", hasSize(4)));
    get("?root=" + feeling1 + "&depth=1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes", hasSize(2)));
  }

  @Test
  void withoutRootTheGraphIsCentredOnThePatient() throws Exception {
    get("")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes", hasSize(4)))
        .andExpect(jsonPath("$.nodes[*].distance", containsInAnyOrder(1, 1, 1, 1)))
        .andExpect(jsonPath("$.edges", hasSize(3)))
        .andExpect(jsonPath("$.truncated").value(false));
  }

  @Test
  void linkTypesAcceptACommaSeparatedListAndRepeatedParameters() throws Exception {
    get("?root=" + event + "&linkTypes=triggers")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes", hasSize(3)))
        .andExpect(jsonPath("$.edges[*].linkType", containsInAnyOrder("triggers", "triggers")));
    get("?root=" + event + "&linkTypes=triggers,co_occurs")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes", hasSize(4)))
        .andExpect(jsonPath("$.edges", hasSize(3)));
    get("?root=" + event + "&linkTypes=co_occurs&linkTypes=triggers")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes", hasSize(4)));
    get("?root=" + event + "&linkTypes=co_occurs")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes", hasSize(2)))
        .andExpect(jsonPath("$.edges", hasSize(1)));
  }

  @Test
  void periodBoundsTheNodesButNotTheRoot() throws Exception {
    get("?root=" + event + "&from=2000-01-01T00:00:00Z&to=2000-12-31T00:00:00Z")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes", hasSize(1)))
        .andExpect(jsonPath("$.nodes[0].id").value(event.toString()))
        .andExpect(jsonPath("$.edges", hasSize(0)));
    get("?root=" + event + "&from=2000-01-01T00:00:00Z&to=2100-12-31T00:00:00Z")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes", hasSize(4)));
  }

  // ---- GET : 422

  @Test
  void depthFourIsRefusedWithAFieldErrorInFrench() throws Exception {
    get("?root=" + event + "&depth=4")
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].field").value("depth"))
        .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("La profondeur doit être comprise entre 1 et 3."));
  }

  @Test
  void depthFourIsRefusedWithAFieldErrorInEnglish() throws Exception {
    get("?root=" + event + "&depth=4", "en")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("depth"))
        .andExpect(jsonPath("$.errors[0].message").value("The depth must be between 1 and 3."));
  }

  @Test
  void depthZeroIsRefused() throws Exception {
    get("?root=" + event + "&depth=0")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("depth"));
  }

  @Test
  void everyInvalidParameterIsReportedWithItsField() throws Exception {
    get("?depth=9&linkTypes=nope&from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("depth", "from", "linkTypes")))
        .andExpect(jsonPath("$.errors[?(@.field == 'linkTypes')].code", contains("INVALID_VALUE")))
        .andExpect(jsonPath("$.errors[?(@.field == 'from')].code", contains("INVALID_TIME")))
        .andExpect(
            jsonPath(
                "$.errors[?(@.field == 'linkTypes')].message",
                contains("Un des types de liens demandés n'existe pas.")));
  }

  // ---- GET : propriété et racine

  @Test
  void aPatientOfAnotherPractitionerIsForbidden() throws Exception {
    UUID strangerId = fx.newPractitioner();
    fx.authenticate(strangerId);
    UUID strangerPatient = fx.newPatient();
    SecurityContextHolder.clearContext();

    send(MockMvcRequestBuilders.get(graph(patient)), null, as(strangerId), null)
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    send(MockMvcRequestBuilders.get(graph(strangerPatient)), null, me, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    send(
            MockMvcRequestBuilders.post(graph(strangerPatient) + "/expand"),
            "{\"memberIds\":[\"%s\"]}".formatted(feeling1),
            me,
            null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
  }

  @Test
  void anUnknownPatientIsNotFound() throws Exception {
    send(MockMvcRequestBuilders.get(graph(UUID.randomUUID())), null, me, null)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
  }

  @Test
  void aRootOfAnotherPatientIsRefused() throws Exception {
    fx.authenticate(fx.practitioner);
    UUID otherPatient = fx.newPatient();
    UUID foreign = fx.entry(otherPatient, fx.session(otherPatient), EntryType.NOTE);
    SecurityContextHolder.clearContext();

    get("?root=" + foreign)
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("DIFFERENT_PATIENT"))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].field").value("root"))
        .andExpect(jsonPath("$.errors[0].code").value("DIFFERENT_PATIENT"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("L'entrée racine n'appartient pas à ce patient."));
  }

  @Test
  void anUnknownRootIsNotFound() throws Exception {
    get("?root=" + UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENTRY_NOT_FOUND"));
  }

  @Test
  void anArchivedRootIsAConflict() throws Exception {
    fx.authenticate(fx.practitioner);
    entries.archive(note);
    SecurityContextHolder.clearContext();

    get("?root=" + note)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ENTRY_ARCHIVED"))
        .andExpect(jsonPath("$.errors[0].field").value("root"));
  }

  @Test
  void requestsWithoutASessionAreUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get(graph(patient)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  // ---- POST : expand

  @Test
  void expandBySuperNodeIdReturnsTheGroup() throws Exception {
    expand("{\"superNodeId\":\"sn:%s:feeling\"}".formatted(event))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.nodes", hasSize(2)))
        .andExpect(
            jsonPath("$.nodes[*].id", containsInAnyOrder(feeling1.toString(), feeling2.toString())))
        .andExpect(jsonPath("$.nodes[*].distance", contains(1, 1)))
        .andExpect(jsonPath("$.nodes[*].degree", contains(1, 1)))
        .andExpect(jsonPath("$.edges", hasSize(2)))
        .andExpect(jsonPath("$.edges[*].source", contains(event.toString(), event.toString())))
        .andExpect(jsonPath("$.superNodes", hasSize(0)))
        .andExpect(jsonPath("$.truncated").value(false));
  }

  @Test
  void expandByMemberIdsReturnsTheGroup() throws Exception {
    expand("{\"memberIds\":[\"%s\",\"%s\"]}".formatted(feeling1, event))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes", hasSize(2)))
        .andExpect(jsonPath("$.nodes[?(@.id == '%s')].degree".formatted(event), contains(3)))
        // les liens incidents à au moins un membre : event-feeling1, event-feeling2, note-event
        .andExpect(jsonPath("$.edges", hasSize(3)));
  }

  @Test
  void anUnknownSuperNodeIsNotFound() throws Exception {
    expand("{\"superNodeId\":\"sn:%s:feeling\"}".formatted(UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("SUPER_NODE_NOT_FOUND"))
        .andExpect(
            jsonPath("$.detail").value("Ce groupe d'entrées est introuvable ou n'existe plus."));
    expand("{\"superNodeId\":\"sn:%s:medication\"}".formatted(event))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUPER_NODE_NOT_FOUND"));
  }

  @Test
  void aMalformedSuperNodeIsRefusedWithItsField() throws Exception {
    expand("{\"superNodeId\":\"pas-un-groupe\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("superNodeId"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
  }

  @Test
  void expandNeedsExactlyOneOfSuperNodeIdAndMemberIds() throws Exception {
    expand("{}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("superNodeId"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    expand("{\"superNodeId\":\"sn:%s:feeling\",\"memberIds\":[\"%s\"]}".formatted(event, feeling1))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("superNodeId"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
  }

  @Test
  void emptyMemberIdsAreRefused() throws Exception {
    expand("{\"memberIds\":[]}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("memberIds"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
  }

  @Test
  void memberIdsOfAnotherPatientAreRefusedWithoutRevealingAnything() throws Exception {
    fx.authenticate(fx.practitioner);
    UUID otherPatient = fx.newPatient();
    UUID foreign = fx.entry(otherPatient, fx.session(otherPatient), EntryType.NOTE);
    SecurityContextHolder.clearContext();

    expand("{\"memberIds\":[\"%s\",\"%s\"]}".formatted(feeling1, foreign))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errors[0].field").value("memberIds"))
        .andExpect(jsonPath("$.errors[0].code").value("ENTRY_NOT_FOUND"));
  }

  @Test
  void anInvalidIdentifierInMemberIdsIsRefusedWithItsField() throws Exception {
    expand("{\"memberIds\":[\"pas-un-uuid\"]}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("memberIds"));
  }

  @Test
  void expandErrorsFollowAcceptLanguage() throws Exception {
    send(
            MockMvcRequestBuilders.post(graph(patient) + "/expand"),
            "{\"superNodeId\":\"sn:%s:feeling\"}".formatted(UUID.randomUUID()),
            me,
            "en")
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.detail").value("This group of entries was not found or no longer exists."));
    send(MockMvcRequestBuilders.post(graph(patient) + "/expand"), "{}", me, "en")
        .andExpect(status().isUnprocessableContent())
        .andExpect(
            jsonPath("$.errors[0].message")
                .value(containsString("Provide exactly one of superNodeId and memberIds")));
  }

  @Test
  void theGraphNeverExposesAnythingButReadOnlyAuthorType() throws Exception {
    get("?root=" + event)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.nodes[*].authorType",
                containsInAnyOrder("practitioner", "practitioner", "practitioner", "practitioner")))
        .andExpect(jsonPath("$.nodes[0].submissionId").doesNotExist());
  }
}
