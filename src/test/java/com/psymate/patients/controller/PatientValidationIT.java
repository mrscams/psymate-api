package com.psymate.patients.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * M10-05 : les 422 du module patients renvoient errors[] = [{field, code, message}] avec les codes
 * des autres modules (REQUIRED, TOO_LONG, INVALID_VALUE, INVALID_DATE, INVALID_FORMAT...), à la
 * création comme à la modification, et le message reste en français.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class PatientValidationIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private RequestPostProcessor auth;
  private UUID tenant;
  private UUID practitionerId;

  @BeforeEach
  void practitioner() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet validation')", tenant);
    UUID id = UUID.randomUUID();
    practitionerId = id;
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist')",
        id,
        tenant,
        email,
        email);
    CurrentPractitioner principal =
        new CurrentPractitioner(id, tenant, email, "Dr Test", "psychologist", "none");
    auth =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String body) throws Exception {
    return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(body).with(auth));
  }

  private String createPatient(String name) throws Exception {
    String res =
        send(post("/api/patients"), "{\"displayName\":\"" + name + "\"}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return res.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
  }

  private static String json(String field, String value) {
    return "{\"" + field + "\":" + value + "}";
  }

  /** Corps, champ en erreur, code attendu, message français attendu. */
  private record Case(String name, String body, String field, String code, String message) {
    Arguments args() {
      return Arguments.of(name, body, field, code, message);
    }
  }

  private static Stream<Arguments> createCases() {
    String x300 = "x".repeat(300);
    String x121 = "x".repeat(121);
    return Stream.of(
            new Case(
                "displayName absent",
                "{\"firstName\":\"X\"}",
                "displayName",
                "REQUIRED",
                "Le nom affiché est obligatoire."),
            new Case(
                "displayName null",
                json("displayName", "null"),
                "displayName",
                "REQUIRED",
                "Le nom affiché est obligatoire."),
            new Case(
                "displayName blanc",
                json("displayName", "\"   \""),
                "displayName",
                "REQUIRED",
                "Le nom affiché est obligatoire."),
            new Case(
                "displayName de 300 caractères",
                json("displayName", "\"" + x300 + "\""),
                "displayName",
                "TOO_LONG",
                "Le nom affiché est limité à 120 caractères."),
            new Case(
                "displayName de 121 caractères",
                json("displayName", "\"" + x121 + "\""),
                "displayName",
                "TOO_LONG",
                "Le nom affiché est limité à 120 caractères."),
            new Case(
                "firstName trop long",
                "{\"displayName\":\"A\",\"firstName\":\"" + x121 + "\"}",
                "firstName",
                "TOO_LONG",
                "Limité à 120 caractères."),
            new Case(
                "lastName trop long",
                "{\"displayName\":\"A\",\"lastName\":\"" + x121 + "\"}",
                "lastName",
                "TOO_LONG",
                "Limité à 120 caractères."),
            new Case(
                "birthDate dans le futur",
                "{\"displayName\":\"A\",\"birthDate\":\"2999-01-01\"}",
                "birthDate",
                "INVALID_DATE",
                "La date de naissance doit être passée et de moins de 120 ans."),
            new Case(
                "birthDate de plus de 120 ans",
                "{\"displayName\":\"A\",\"birthDate\":\"1800-01-01\"}",
                "birthDate",
                "INVALID_DATE",
                "La date de naissance doit être passée et de moins de 120 ans."),
            new Case(
                "birthDate illisible",
                "{\"displayName\":\"A\",\"birthDate\":\"demain\"}",
                "birthDate",
                "INVALID_FORMAT",
                "Valeur invalide."),
            new Case(
                "tags qui n'est pas une liste",
                "{\"displayName\":\"A\",\"tags\":{\"a\":1}}",
                "tags",
                "INVALID_FORMAT",
                "Valeur invalide."),
            new Case(
                "pronoun hors liste",
                "{\"displayName\":\"A\",\"pronoun\":\"ielle\"}",
                "pronoun",
                "INVALID_VALUE",
                "Pronom inconnu : elle, il, iel ou neutre."),
            new Case(
                "flag sans nature",
                "{\"displayName\":\"A\",\"flags\":[{\"reason\":\"x\"}]}",
                "flags[0].kind",
                "REQUIRED",
                "La nature du drapeau est obligatoire (watch, risk ou shared_care)."),
            new Case(
                "flag de nature inconnue",
                "{\"displayName\":\"A\",\"flags\":[{\"kind\":\"danger\"}]}",
                "flags[0].kind",
                "INVALID_VALUE",
                "Drapeau inconnu : watch, risk ou shared_care."),
            new Case(
                "flag watch sans raison",
                "{\"displayName\":\"A\",\"flags\":[{\"kind\":\"watch\"}]}",
                "flags[0].reason",
                "REQUIRED",
                "Une raison est obligatoire pour « à surveiller »."),
            new Case(
                "tag vide",
                "{\"displayName\":\"A\",\"tags\":[\" \"]}",
                "tags[0]",
                "REQUIRED",
                "Le libellé du tag est obligatoire."),
            new Case(
                "tag inconnu",
                "{\"displayName\":\"A\",\"tags\":[\"tag-jamais-cree-m10-05\"]}",
                "tags[0]",
                "TAG_UNKNOWN",
                "Ce tag n'existe pas."),
            new Case(
                "tag égal à une émotion",
                "{\"displayName\":\"A\",\"tags\":[\"anxiété\"]}",
                "tags[0]",
                "TAG_IS_EMOTION",
                "Ce libellé correspond à l'émotion « Anxiété »."))
        .map(Case::args);
  }

  @ParameterizedTest(name = "POST : {0}")
  @MethodSource("createCases")
  void createReturnsAFieldProblemWithItsCode(
      String name, String body, String field, String code, String message) throws Exception {
    send(post("/api/patients"), body)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].code").value(code))
        .andExpect(jsonPath("$.errors[0].message").value(message));
  }

  private static Stream<Arguments> patchCases() {
    String x300 = "x".repeat(300);
    return Stream.of(
            new Case(
                "displayName vide",
                json("displayName", "\"\""),
                "displayName",
                "REQUIRED",
                "Le nom affiché est obligatoire."),
            new Case(
                "displayName null",
                json("displayName", "null"),
                "displayName",
                "REQUIRED",
                "Le nom affiché est obligatoire."),
            new Case(
                "displayName de 300 caractères",
                json("displayName", "\"" + x300 + "\""),
                "displayName",
                "TOO_LONG",
                "Le nom affiché est limité à 120 caractères."),
            new Case(
                "firstName trop long",
                json("firstName", "\"" + x300 + "\""),
                "firstName",
                "TOO_LONG",
                "Limité à 120 caractères."),
            new Case(
                "lastName trop long",
                json("lastName", "\"" + x300 + "\""),
                "lastName",
                "TOO_LONG",
                "Limité à 120 caractères."),
            new Case(
                "birthDate dans le futur",
                json("birthDate", "\"2999-01-01\""),
                "birthDate",
                "INVALID_DATE",
                "La date de naissance doit être passée et de moins de 120 ans."),
            new Case(
                "birthDate illisible",
                json("birthDate", "\"demain\""),
                "birthDate",
                "INVALID_FORMAT",
                "Date invalide (AAAA-MM-JJ)."),
            new Case(
                "birthDate chaîne vide",
                json("birthDate", "\"\""),
                "birthDate",
                "INVALID_FORMAT",
                "Date invalide (AAAA-MM-JJ)."),
            new Case(
                "pronoun hors liste",
                json("pronoun", "\"madame\""),
                "pronoun",
                "INVALID_VALUE",
                "Pronom inconnu : elle, il, iel ou neutre."),
            new Case(
                "clé inconnue",
                json("authorType", "\"patient\""),
                "authorType",
                "UNKNOWN_FIELD",
                "Champ non modifiable ou inconnu."),
            new Case(
                "context qui n'est pas un objet",
                json("context", "\"texte\""),
                "context",
                "INVALID_FORMAT",
                "Un objet est attendu."),
            new Case(
                "followUp qui n'est pas un objet",
                json("followUp", "[1]"),
                "followUp",
                "INVALID_FORMAT",
                "Un objet est attendu."))
        .map(Case::args);
  }

  @ParameterizedTest(name = "PATCH : {0}")
  @MethodSource("patchCases")
  void patchFollowsTheSameRules(String name, String body, String field, String code, String message)
      throws Exception {
    String id = createPatient("Patch " + name);
    send(patch("/api/patients/" + id), body)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].code").value(code))
        .andExpect(jsonPath("$.errors[0].message").value(message));
  }

  @Test
  void everyFaultyFieldIsReportedWithItsOwnCode() throws Exception {
    send(
            post("/api/patients"),
            "{\"displayName\":\""
                + "x".repeat(300)
                + "\",\"birthDate\":\"2999-01-01\","
                + "\"pronoun\":\"zzz\",\"flags\":[{\"kind\":\"watch\"}]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(4))
        .andExpect(jsonPath("$.errors[?(@.field=='displayName')].code").value("TOO_LONG"))
        .andExpect(jsonPath("$.errors[?(@.field=='birthDate')].code").value("INVALID_DATE"))
        .andExpect(jsonPath("$.errors[?(@.field=='pronoun')].code").value("INVALID_VALUE"))
        .andExpect(jsonPath("$.errors[?(@.field=='flags[0].reason')].code").value("REQUIRED"));
  }

  @Test
  void validValuesAreAccepted() throws Exception {
    for (String pronoun : List.of("elle", "il", "iel", "neutre")) {
      send(
              post("/api/patients"),
              "{\"displayName\":\"" + "x".repeat(120) + "\",\"pronoun\":\"" + pronoun + "\"}")
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.pronoun").value(pronoun));
    }
    String id = createPatient("Valide");
    send(patch("/api/patients/" + id), "{\"pronoun\":null,\"birthDate\":null}")
        .andExpect(status().isOk());
    send(patch("/api/patients/" + id), "{\"pronoun\":\"\"}").andExpect(status().isOk());
  }

  @Test
  void pronounCaseAndSpacesAreToleratedAndStoredInLowerCase() throws Exception {
    String body = "{\"displayName\":\"Casse\",\"pronoun\":\" ELLE \"}";
    String res =
        send(post("/api/patients"), body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pronoun").value("elle"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = res.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
    send(post("/api/patients"), "{\"displayName\":\"Casse 2\",\"pronoun\":\"Neutre\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.pronoun").value("neutre"));
    send(patch("/api/patients/" + id), "{\"pronoun\":\"Iel\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pronoun").value("iel"));
    send(patch("/api/patients/" + id), "{\"pronoun\":\" IL \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pronoun").value("il"));
    Integer stored =
        jdbc.queryForObject(
            "select count(*) from patient where id = ? and pronoun = 'il'",
            Integer.class,
            UUID.fromString(id));
    assertThat(stored).isEqualTo(1);
  }

  @Test
  void unreadableBodyWithoutAFieldStaysA400() throws Exception {
    send(post("/api/patients"), "{\"displayName\":")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
  }

  /** Patient enregistré avant les limites de G Q47 : pronom hors liste, noms de 130 caractères. */
  private String legacyPatient() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name, first_name, last_name,"
            + " pronoun) values (?, ?, ?, ?, ?, ?, 'ielle')",
        id,
        tenant,
        practitionerId,
        "d".repeat(130),
        "f".repeat(130),
        "l".repeat(130));
    return id.toString();
  }

  @Test
  void aLegacyPatientStaysReadableAndEditable() throws Exception {
    String id = legacyPatient();
    mvc.perform(get("/api/patients/" + id).with(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pronoun").value("ielle"))
        .andExpect(jsonPath("$.displayName").value("d".repeat(130)));
    send(patch("/api/patients/" + id), "{\"phone\":\"0102030405\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value("0102030405"))
        .andExpect(jsonPath("$.pronoun").value("ielle"))
        .andExpect(jsonPath("$.firstName").value("f".repeat(130)));
  }

  @Test
  void patchResendingUnchangedLegacyValuesIsNotRevalidated() throws Exception {
    String id = legacyPatient();
    String whole =
        "{\"displayName\":\""
            + "d".repeat(130)
            + "\",\"firstName\":\""
            + "f".repeat(130)
            + "\",\"lastName\":\""
            + "l".repeat(130)
            + "\",\"pronoun\":\"ielle\","
            + "\"phone\":\"0102\"}";
    send(patch("/api/patients/" + id), whole)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value("0102"))
        .andExpect(jsonPath("$.pronoun").value("ielle"));
    // Dès qu'une valeur change, la règle s'applique de nouveau.
    send(patch("/api/patients/" + id), "{\"pronoun\":\"madame\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    send(patch("/api/patients/" + id), "{\"lastName\":\"" + "m".repeat(130) + "\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
    send(patch("/api/patients/" + id), "{\"displayName\":\"" + "e".repeat(130) + "\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
    // Un nom ou un pronom valide remplace sans difficulté la valeur héritée.
    send(patch("/api/patients/" + id), "{\"pronoun\":\"Elle\",\"displayName\":\"Court\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pronoun").value("elle"))
        .andExpect(jsonPath("$.displayName").value("Court"));
  }

  @Test
  void otherEndpointsUseTheSameCodes() throws Exception {
    String id = createPatient("Autres");
    send(post("/api/patients/" + id + "/flags"), "{\"kind\":\"danger\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("flag.kind"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    send(put("/api/patients/" + id + "/tags"), "{\"tags\":[\"\"]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("tags[0]"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    mvc.perform(get("/api/patients").param("sort", "taille").with(auth))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("sort"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    mvc.perform(get("/api/patients").param("cursor", "%%%").with(auth))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("cursor"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
  }
}
