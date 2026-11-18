package com.psymate.patients.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Profil 360 (M13-02, J3) de bout en bout sur une base réelle : les quatre routes, chaque code
 * d'erreur, le tri de l'onglet, le plafond (abaissé à {@value #CAP} par propriété), le patient
 * archivé et l'isolation entre deux praticiens. Les patients sont insérés en SQL : aucune séance
 * d'accueil n'est créée, donc toute ligne d'entry ou de link pour eux viendrait du profil.
 */
@SpringBootTest(
    properties = "app.patients.profile.max-active-items=" + PatientProfileControllerIT.CAP)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class PatientProfileControllerIT {

  static final int CAP = 8;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;
  private UUID alice;
  private UUID bob;
  private RequestPostProcessor auth;

  @BeforeEach
  void practitioners() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    alice = insertPractitioner();
    bob = insertPractitioner();
    as(alice);
  }

  // ---- outils

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

  private UUID insertPatient(UUID owner) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'P')",
        id,
        tenant,
        owner);
    return id;
  }

  private static String items(UUID patient) {
    return "/api/patients/" + patient + "/profile-items";
  }

  private static String item(UUID patient, String itemId) {
    return items(patient) + "/" + itemId;
  }

  private ResultActions send(MockHttpServletRequestBuilder b, String body) throws Exception {
    return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(body).with(auth));
  }

  private ResultActions read(UUID patient) throws Exception {
    return mvc.perform(get(items(patient)).with(auth));
  }

  private String create(UUID patient, String json) throws Exception {
    String body =
        send(post(items(patient)), json)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private String create(UUID patient, String category, String title) throws Exception {
    return create(patient, "{\"category\":\"" + category + "\",\"title\":\"" + title + "\"}");
  }

  /** 422 VALIDATION_FAILED avec une seule erreur, sur ce champ et avec ce code. */
  private static void expectField(ResultActions r, String field, String code) throws Exception {
    r.andExpect(status().isUnprocessableContent())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].code").value(code))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
  }

  private static void expectCode(ResultActions r, int status, String code) throws Exception {
    r.andExpect(status().is(status))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(status))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.detail").isNotEmpty());
  }

  private int count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Integer.class, args);
  }

  private Map<String, Object> row(String itemId) {
    return jdbc.queryForMap(
        "select * from patient_profile_item where id = ?", UUID.fromString(itemId));
  }

  // ---- lecture et création

  @Test
  void anEmptyProfileReadsAsAnEmptyList() throws Exception {
    UUID patient = insertPatient(alice);
    read(patient)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.patientId").value(patient.toString()))
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void postCreatesAnItemWithLocationAndGetReturnsIt() throws Exception {
    UUID patient = insertPatient(alice);
    String body =
        send(
                post(items(patient)),
                """
                {"category":"history","title":"  A grandi à Nantes  ","detail":"Maison pleine",
                 "periodLabel":"Enfance","periodYear":1998}""")
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", startsWith(items(patient) + "/")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(body, "$.id");
    assertThat(JsonPath.<String>read(body, "$.category")).isEqualTo("history");
    assertThat(JsonPath.<String>read(body, "$.title")).isEqualTo("A grandi à Nantes");
    assertThat(JsonPath.<String>read(body, "$.detail")).isEqualTo("Maison pleine");
    assertThat(JsonPath.<String>read(body, "$.periodLabel")).isEqualTo("Enfance");
    assertThat(JsonPath.<Integer>read(body, "$.periodYear")).isEqualTo(1998);
    assertThat(JsonPath.<String>read(body, "$.createdAt"))
        .isEqualTo(JsonPath.read(body, "$.updatedAt"));

    read(patient)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id))
        .andExpect(jsonPath("$.items[0].title").value("A grandi à Nantes"))
        .andExpect(
            jsonPath("$.items[0].createdAt").value(JsonPath.<String>read(body, "$.createdAt")));

    Map<String, Object> row = row(id);
    assertThat(row.get("tenant_id")).isEqualTo(tenant);
    assertThat(row.get("patient_id")).isEqualTo(patient);
    assertThat(row.get("created_by")).isEqualTo(alice);
    assertThat(row.get("archived_at")).isNull();
  }

  @Test
  void locationPointsToTheCreatedItem() throws Exception {
    UUID patient = insertPatient(alice);
    String location =
        send(post(items(patient)), "{\"category\":\"family\",\"title\":\"Une sœur, Léa\"}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Location");
    String id = location.substring(location.lastIndexOf('/') + 1);
    assertThat(location).isEqualTo("/api/patients/" + patient + "/profile-items/" + id);
    assertThat(count("select count(*) from patient_profile_item where id = ?", UUID.fromString(id)))
        .isEqualTo(1);
  }

  @Test
  void theProfileIsSortedByCategoryThenYearThenCreation() throws Exception {
    UUID patient = insertPatient(alice);
    String goal = create(patient, "values_goals", "Reprendre le piano");
    String y2002 =
        create(patient, "{\"category\":\"history\",\"title\":\"Lycée\",\"periodYear\":2002}");
    String sister = create(patient, "family", "Une sœur, Léa");
    String undated =
        create(
            patient,
            "{\"category\":\"history\",\"title\":\"Un été chez sa grand-mère\","
                + "\"periodLabel\":\"Un été\"}");
    String childhood =
        create(
            patient,
            "{\"category\":\"history\",\"title\":\"Enfance à Nantes\","
                + "\"periodLabel\":\"Enfance\",\"periodYear\":1998}");
    String health = create(patient, "health", "Asthme dans l'enfance");
    String y1991 =
        create(patient, "{\"category\":\"history\",\"title\":\"Naissance\",\"periodYear\":1991}");
    String brother = create(patient, "family", "Un frère, Tom");

    String body =
        read(patient).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    List<String> ids = JsonPath.read(body, "$.items[*].id");
    assertThat(ids)
        .containsExactly(y1991, childhood, y2002, undated, sister, brother, health, goal);
  }

  // ---- validation

  @Test
  void aPeriodOutsideHistoryIsInvalidValueOnTheField() throws Exception {
    UUID patient = insertPatient(alice);
    for (String category : List.of("family", "health", "values_goals")) {
      expectField(
          send(
              post(items(patient)),
              "{\"category\":\"" + category + "\",\"title\":\"T\",\"periodLabel\":\"Enfance\"}"),
          "periodLabel",
          "INVALID_VALUE");
      expectField(
          send(
              post(items(patient)),
              "{\"category\":\"" + category + "\",\"title\":\"T\",\"periodYear\":1998}"),
          "periodYear",
          "INVALID_VALUE");
    }
    assertThat(count("select count(*) from patient_profile_item where patient_id = ?", patient))
        .isZero();
  }

  @Test
  void aMissingOrBlankTitleIsRequired() throws Exception {
    UUID patient = insertPatient(alice);
    expectField(send(post(items(patient)), "{\"category\":\"family\"}"), "title", "REQUIRED");
    expectField(
        send(post(items(patient)), "{\"category\":\"family\",\"title\":\"   \"}"),
        "title",
        "REQUIRED");
    expectField(
        send(post(items(patient)), "{\"category\":\"family\",\"title\":null}"),
        "title",
        "REQUIRED");
  }

  @Test
  void aMissingOrUnknownCategoryIsRefused() throws Exception {
    UUID patient = insertPatient(alice);
    expectField(send(post(items(patient)), "{\"title\":\"T\"}"), "category", "REQUIRED");
    expectField(
        send(post(items(patient)), "{\"category\":\"resources\",\"title\":\"T\"}"),
        "category",
        "INVALID_VALUE");
  }

  @Test
  void lengthsAndYearBoundsAreOutOfRange() throws Exception {
    UUID patient = insertPatient(alice);
    expectField(
        send(
            post(items(patient)),
            "{\"category\":\"family\",\"title\":\"" + "x".repeat(201) + "\"}"),
        "title",
        "OUT_OF_RANGE");
    expectField(
        send(
            post(items(patient)),
            "{\"category\":\"family\",\"title\":\"T\",\"detail\":\"" + "x".repeat(2001) + "\"}"),
        "detail",
        "OUT_OF_RANGE");
    expectField(
        send(
            post(items(patient)),
            "{\"category\":\"history\",\"title\":\"T\",\"periodLabel\":\""
                + "x".repeat(61)
                + "\"}"),
        "periodLabel",
        "OUT_OF_RANGE");
    for (int year : new int[] {1899, 2101}) {
      expectField(
          send(
              post(items(patient)),
              "{\"category\":\"history\",\"title\":\"T\",\"periodYear\":" + year + "}"),
          "periodYear",
          "OUT_OF_RANGE");
    }
    // Les bornes elles-mêmes passent, et la longueur se compte après retrait des espaces de bord.
    create(
        patient,
        "{\"category\":\"history\",\"title\":\"  "
            + "x".repeat(200)
            + "  \",\"detail\":\""
            + "d".repeat(2000)
            + "\",\"periodLabel\":\""
            + "p".repeat(60)
            + "\",\"periodYear\":2100}");
    create(patient, "{\"category\":\"history\",\"title\":\"T\",\"periodYear\":1900}");
  }

  @Test
  void severalInvalidFieldsAreListedInFieldOrder() throws Exception {
    UUID patient = insertPatient(alice);
    send(
            post(items(patient)),
            "{\"category\":\"family\",\"title\":\"\",\"detail\":\""
                + "x".repeat(2001)
                + "\",\"periodYear\":1998}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[*].field").value(List.of("title", "detail", "periodYear")))
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(List.of("REQUIRED", "OUT_OF_RANGE", "INVALID_VALUE")));
  }

  @Test
  void anUnknownFieldIsRefusedOnPostAndPatch() throws Exception {
    UUID patient = insertPatient(alice);
    expectField(
        send(
            post(items(patient)), "{\"category\":\"family\",\"title\":\"T\",\"authorType\":\"x\"}"),
        "authorType",
        "UNKNOWN_FIELD");
    String id = create(patient, "family", "Une sœur");
    expectField(
        send(patch(item(patient, id)), "{\"patientId\":\"" + UUID.randomUUID() + "\"}"),
        "patientId",
        "UNKNOWN_FIELD");
    assertThat(count("select count(*) from patient_profile_item where patient_id = ?", patient))
        .isEqualTo(1);
  }

  @Test
  void anUnreadableYearIsInvalidFormat() throws Exception {
    UUID patient = insertPatient(alice);
    expectField(
        send(
            post(items(patient)),
            "{\"category\":\"history\",\"title\":\"T\",\"periodYear\":\"vers 2000\"}"),
        "periodYear",
        "INVALID_FORMAT");
  }

  // ---- correction

  @Test
  void patchCorrectsInPlaceWithoutVersionEventOrClinicalRev() throws Exception {
    UUID patient = insertPatient(alice);
    String id =
        create(
            patient,
            "{\"category\":\"history\",\"title\":\"Enfance\",\"detail\":\"Nantes\","
                + "\"periodLabel\":\"Enfance\",\"periodYear\":1998}");
    Map<String, Object> before = row(id);
    Map<String, Object> patientBefore =
        jdbc.queryForMap(
            "select current_version, clinical_rev, updated_at from patient where id = ?", patient);

    send(patch(item(patient, id)), "{\"title\":\"Enfance à Nantes\",\"detail\":null}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.title").value("Enfance à Nantes"))
        .andExpect(jsonPath("$.detail").doesNotExist())
        .andExpect(jsonPath("$.periodLabel").value("Enfance"))
        .andExpect(jsonPath("$.periodYear").value(1998));

    Map<String, Object> after = row(id);
    assertThat(count("select count(*) from patient_profile_item where patient_id = ?", patient))
        .isEqualTo(1);
    assertThat(after.get("title")).isEqualTo("Enfance à Nantes");
    assertThat(after.get("detail")).isNull();
    assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
    assertThat(((java.sql.Timestamp) after.get("updated_at")).toInstant())
        .isAfter(((java.sql.Timestamp) before.get("updated_at")).toInstant());
    assertThat(
            jdbc.queryForMap(
                "select current_version, clinical_rev, updated_at from patient where id = ?",
                patient))
        .isEqualTo(patientBefore);
    assertThat(count("select count(*) from patient_version where patient_id = ?", patient))
        .isZero();
    assertThat(
            count(
                "select count(*) from event_publication where serialized_event like ?",
                "%" + patient + "%"))
        .isZero();
  }

  @Test
  void patchLeavingHistoryClearsThePeriod() throws Exception {
    UUID patient = insertPatient(alice);
    String id =
        create(
            patient,
            "{\"category\":\"history\",\"title\":\"Sa grand-mère\","
                + "\"periodLabel\":\"Enfance\",\"periodYear\":1998}");
    send(patch(item(patient, id)), "{\"category\":\"family\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").value("family"))
        .andExpect(jsonPath("$.periodLabel").doesNotExist())
        .andExpect(jsonPath("$.periodYear").doesNotExist());
    Map<String, Object> row = row(id);
    assertThat(row.get("category")).isEqualTo("family");
    assertThat(row.get("period_label")).isNull();
    assertThat(row.get("period_year")).isNull();
  }

  @Test
  void patchSendingAPeriodToANonHistoryItemIsInvalidValue() throws Exception {
    UUID patient = insertPatient(alice);
    String id = create(patient, "health", "Asthme");
    expectField(
        send(patch(item(patient, id)), "{\"periodYear\":2004}"), "periodYear", "INVALID_VALUE");
    expectField(
        send(patch(item(patient, id)), "{\"category\":\"values_goals\",\"periodLabel\":\"2004\"}"),
        "periodLabel",
        "INVALID_VALUE");
    expectField(send(patch(item(patient, id)), "{\"title\":\"\"}"), "title", "REQUIRED");
    expectField(send(patch(item(patient, id)), "{\"category\":null}"), "category", "REQUIRED");
    expectField(
        send(patch(item(patient, id)), "{\"category\":\"resources\"}"),
        "category",
        "INVALID_VALUE");
    expectField(
        send(patch(item(patient, id)), "{\"title\":\"" + "x".repeat(201) + "\"}"),
        "title",
        "OUT_OF_RANGE");
    assertThat(row(id).get("title")).isEqualTo("Asthme");
  }

  @Test
  void anArchivedItemCannotBeCorrected() throws Exception {
    UUID patient = insertPatient(alice);
    String id = create(patient, "family", "Un frère");
    mvc.perform(delete(item(patient, id)).with(auth)).andExpect(status().isNoContent());
    expectCode(
        send(patch(item(patient, id)), "{\"title\":\"Deux frères\"}"),
        409,
        "PROFILE_ITEM_ARCHIVED");
    assertThat(row(id).get("title")).isEqualTo("Un frère");
  }

  // ---- retrait

  @Test
  void deleteArchivesTheItemKeepsTheRowAndIsIdempotent() throws Exception {
    UUID patient = insertPatient(alice);
    String kept = create(patient, "family", "Une sœur");
    String gone = create(patient, "family", "Un frère");

    mvc.perform(delete(item(patient, gone)).with(auth))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    read(patient)
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(kept));
    Object archivedAt = row(gone).get("archived_at");
    assertThat(archivedAt).isNotNull();
    assertThat(count("select count(*) from patient_profile_item where patient_id = ?", patient))
        .isEqualTo(2);

    mvc.perform(delete(item(patient, gone)).with(auth)).andExpect(status().isNoContent());
    assertThat(row(gone).get("archived_at")).isEqualTo(archivedAt);
  }

  // ---- information introuvable

  @Test
  void anItemOfAnotherPatientIsProfileItemNotFound() throws Exception {
    UUID mine = insertPatient(alice);
    UUID other = insertPatient(alice);
    String foreign = create(other, "family", "Une sœur");
    expectCode(
        send(patch(item(mine, foreign)), "{\"title\":\"Volé\"}"), 404, "PROFILE_ITEM_NOT_FOUND");
    expectCode(mvc.perform(delete(item(mine, foreign)).with(auth)), 404, "PROFILE_ITEM_NOT_FOUND");
    expectCode(
        send(patch(item(mine, UUID.randomUUID().toString())), "{\"title\":\"T\"}"),
        404,
        "PROFILE_ITEM_NOT_FOUND");
    assertThat(row(foreign).get("title")).isEqualTo("Une sœur");
    assertThat(row(foreign).get("archived_at")).isNull();
  }

  @Test
  void errorMessagesFollowAcceptLanguage() throws Exception {
    UUID patient = insertPatient(alice);
    String missing = UUID.randomUUID().toString();
    mvc.perform(delete(item(patient, missing)).with(auth))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.detail").value("Information de profil introuvable pour ce patient."));
    mvc.perform(delete(item(patient, missing)).header("Accept-Language", "en").with(auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Profile information not found for this patient."));
  }

  // ---- patient : propriété et archivage

  @Test
  void anotherPractitionersPatientIsForbiddenOnEveryRoute() throws Exception {
    UUID alices = insertPatient(alice);
    String id = create(alices, "family", "Une sœur");
    as(bob);
    expectCode(read(alices), 403, "PATIENT_FORBIDDEN");
    expectCode(
        send(post(items(alices)), "{\"category\":\"family\",\"title\":\"T\"}"),
        403,
        "PATIENT_FORBIDDEN");
    expectCode(send(patch(item(alices, id)), "{\"title\":\"T\"}"), 403, "PATIENT_FORBIDDEN");
    expectCode(mvc.perform(delete(item(alices, id)).with(auth)), 403, "PATIENT_FORBIDDEN");

    UUID bobs = insertPatient(bob);
    expectCode(send(patch(item(bobs, id)), "{\"title\":\"T\"}"), 404, "PROFILE_ITEM_NOT_FOUND");
    read(bobs).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    assertThat(row(id).get("title")).isEqualTo("Une sœur");
    assertThat(row(id).get("archived_at")).isNull();
  }

  @Test
  void anUnknownPatientIsNotFoundOnEveryRoute() throws Exception {
    UUID unknown = UUID.randomUUID();
    String id = UUID.randomUUID().toString();
    expectCode(read(unknown), 404, "PATIENT_NOT_FOUND");
    expectCode(
        send(post(items(unknown)), "{\"category\":\"family\",\"title\":\"T\"}"),
        404,
        "PATIENT_NOT_FOUND");
    expectCode(send(patch(item(unknown, id)), "{\"title\":\"T\"}"), 404, "PATIENT_NOT_FOUND");
    expectCode(mvc.perform(delete(item(unknown, id)).with(auth)), 404, "PATIENT_NOT_FOUND");
  }

  @Test
  void anArchivedPatientIsReadableButRefusesWrites() throws Exception {
    UUID patient = insertPatient(alice);
    String id = create(patient, "family", "Une sœur");
    jdbc.update("update patient set archived_at = now() where id = ?", patient);

    read(patient).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    expectCode(
        send(post(items(patient)), "{\"category\":\"family\",\"title\":\"T\"}"),
        409,
        "PATIENT_ARCHIVED");
    expectCode(send(patch(item(patient, id)), "{\"title\":\"T\"}"), 409, "PATIENT_ARCHIVED");
    expectCode(mvc.perform(delete(item(patient, id)).with(auth)), 409, "PATIENT_ARCHIVED");
    assertThat(row(id).get("title")).isEqualTo("Une sœur");
    assertThat(row(id).get("archived_at")).isNull();
  }

  // ---- plafond

  @Test
  void theActiveItemCapIsProfileItemLimitReached() throws Exception {
    UUID patient = insertPatient(alice);
    String first = null;
    for (int i = 0; i < CAP; i++) {
      String id = create(patient, "family", "Proche " + i);
      first = first == null ? id : first;
    }
    expectCode(
        send(post(items(patient)), "{\"category\":\"family\",\"title\":\"De trop\"}"),
        422,
        "PROFILE_ITEM_LIMIT_REACHED");
    assertThat(count("select count(*) from patient_profile_item where patient_id = ?", patient))
        .isEqualTo(CAP);

    // Une information retirée ne compte plus.
    mvc.perform(delete(item(patient, first)).with(auth)).andExpect(status().isNoContent());
    create(patient, "family", "Enfin");
  }

  // ---- séance intouchée

  @Test
  void noEntryEntryVersionNorLinkIsWritten() throws Exception {
    UUID patient = insertPatient(alice);
    Map<String, Object> patientBefore = patientRow(patient);
    String id =
        create(patient, "{\"category\":\"history\",\"title\":\"Naissance\",\"periodYear\":1991}");
    send(patch(item(patient, id)), "{\"category\":\"health\"}").andExpect(status().isOk());
    mvc.perform(delete(item(patient, id)).with(auth)).andExpect(status().isNoContent());
    read(patient).andExpect(status().isOk());

    assertThat(count("select count(*) from entry where patient_id = ?", patient)).isZero();
    assertThat(
            count(
                "select count(*) from entry_version v join entry e on e.id = v.entry_id"
                    + " where e.patient_id = ?",
                patient))
        .isZero();
    assertThat(count("select count(*) from link where patient_id = ?", patient)).isZero();
    assertThat(count("select count(*) from patient_version where patient_id = ?", patient))
        .isZero();
    // La fiche est verrouillée à l'ajout (plafond) mais jamais écrite.
    assertThat(patientRow(patient)).isEqualTo(patientBefore);
  }

  private Map<String, Object> patientRow(UUID patient) {
    return jdbc.queryForMap(
        "select current_version, clinical_rev, updated_at, archived_at from patient where id = ?",
        patient);
  }
}
