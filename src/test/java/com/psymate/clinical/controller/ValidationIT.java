package com.psymate.clinical.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Validation des corps de requête : 422 problem+json, une entrée errors[] par champ fautif. */
class ValidationIT extends ClinicalWebSupport {

  private String session;

  @BeforeEach
  void openSession() throws Exception {
    session = startSession(patient);
  }

  private ResultActions create(String json) throws Exception {
    return post(entriesUrl(patient), json);
  }

  private int entriesOfPatient() {
    return count("select count(*) from entry where patient_id = ?", patient);
  }

  // ---- une erreur par champ

  @Test
  void intensityOutOfRangeAndUnknownEmotionGiveTwoErrors() throws Exception {
    create("{\"type\":\"feeling\",\"feeling\":{\"emotionCode\":\"nope\",\"intensity\":11}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(containsInAnyOrder("feeling.intensity", "feeling.emotionCode")))
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(containsInAnyOrder("OUT_OF_RANGE", "UNKNOWN_EMOTION")))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
        .andExpect(jsonPath("$.errors[1].message").isNotEmpty());
    assertThat(entriesOfPatient()).as("rien n'est écrit").isEqualTo(1);
  }

  @Test
  void messagesAreInFrenchByDefaultAndInEnglishOnRequest() throws Exception {
    String body = "{\"type\":\"feeling\",\"feeling\":{\"emotionCode\":\"nope\",\"intensity\":3}}";
    create(body)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("Cette émotion n'existe pas dans votre catalogue."))
        .andExpect(jsonPath("$.title").value("Données invalides"));
    send(MockMvcRequestBuilders.post(entriesUrl(patient)).header("Accept-Language", "en"), body, me)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("This emotion does not exist in your catalogue."));
  }

  // ---- champs obligatoires par type

  @Test
  void aNoteWithoutBodyIsRefused() throws Exception {
    create("{\"type\":\"note\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("body"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
  }

  @Test
  void anEventWithoutTitleIsRefused() throws Exception {
    create("{\"type\":\"event\",\"body\":\"sans titre\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("title"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
  }

  @Test
  void aSituationNeedsATitleAndAStartDate() throws Exception {
    create("{\"type\":\"situation\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(
            jsonPath("$.errors[*].field").value(containsInAnyOrder("title", "occurred.from")));
    create("{\"type\":\"situation\",\"title\":\"T\",\"occurred\":{\"precision\":\"month\"}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("occurred.from"));
  }

  @Test
  void aFeelingNeedsEmotionAndIntensity() throws Exception {
    create("{\"type\":\"feeling\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("feeling"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    create("{\"type\":\"feeling\",\"feeling\":{}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(containsInAnyOrder("feeling.emotionCode", "feeling.intensity")));
  }

  @Test
  void aMedicationNeedsAMolecule() throws Exception {
    create("{\"type\":\"medication\",\"medication\":{\"dosage\":\"50 mg\"}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("medication.molecule"))
        .andExpect(jsonPath("$.errors[0].message").value("La molécule est obligatoire."));
  }

  @Test
  void aSessionNeedsAModality() throws Exception {
    create("{\"type\":\"session\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("session"));
    create("{\"type\":\"session\",\"session\":{\"modality\":\"carrier_pigeon\"}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("session.modality"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
  }

  @Test
  void typeIsRequiredAndMustBeKnown() throws Exception {
    create("{\"body\":\"x\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("type"))
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    create("{\"type\":\"diary\",\"body\":\"x\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("type"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
  }

  @Test
  void aDetailBlockOfAnotherTypeIsRefused() throws Exception {
    create(
            "{\"type\":\"note\",\"body\":\"x\",\"feeling\":{\"emotionCode\":\"anxiety\",\"intensity\":3}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("feeling"))
        .andExpect(jsonPath("$.errors[0].code").value("DETAIL_NOT_ALLOWED"));
  }

  @Test
  void unknownStatusVisibilityAndPrecisionAreRefusedTogether() throws Exception {
    create(
            """
            {"type":"note","body":"x","status":"maybe","visibility":"public",
             "occurred":{"from":"2026-10-20T19:00:00Z","precision":"fortnight"}}
            """)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(containsInAnyOrder("status", "visibility", "occurred.precision")));
  }

  @Test
  void anEndBeforeTheStartIsRefused() throws Exception {
    create(
            """
            {"type":"event","title":"T",
             "occurred":{"from":"2026-10-20T19:00:00Z","to":"2026-10-19T19:00:00Z"}}
            """)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("occurred.to"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_TIME"));
  }

  // ---- format

  @Test
  void aMalformedDateOrUuidPointsAtTheField() throws Exception {
    create("{\"type\":\"note\",\"body\":\"x\",\"occurred\":{\"from\":\"hier soir\"}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("occurred.from"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    create("{\"type\":\"note\",\"body\":\"x\",\"sessionId\":\"pas-un-uuid\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("sessionId"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
  }

  @Test
  void brokenJsonIsA400AndOtherUnknownKeysAreIgnored() throws Exception {
    create("{\"type\":\"note\",")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    create("{\"type\":\"note\",\"body\":\"x\",\"surprise\":42}").andExpect(status().isCreated());
  }

  // ---- tags

  @Test
  void anUnknownTagIsRefusedWithItsIndex() throws Exception {
    createTag("travail");
    create("{\"type\":\"note\",\"body\":\"x\",\"tags\":[\"travail\",\"inconnu\"]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("tags[1]"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_TAG"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("/api/tags")));
    assertThat(entriesOfPatient()).isEqualTo(1);
  }

  // ---- champs interdits en écriture (A11) et liens (M2-02)

  @Test
  void authorTypeInACreationBodyIsRefused() throws Exception {
    create("{\"type\":\"note\",\"body\":\"x\",\"authorType\":\"practitioner\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("authorType"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));
    create("{\"type\":\"note\",\"body\":\"x\",\"authorType\":\"patient\"}")
        .andExpect(status().isUnprocessableEntity());
    assertThat(entriesOfPatient()).as("rien n'est écrit").isEqualTo(1);
  }

  @Test
  void submissionIdInAPatchBodyIsRefused() throws Exception {
    String id = createEntry(patient, "{\"type\":\"note\",\"body\":\"x\"}");
    patch("/api/entries/" + id, "{\"body\":\"y\",\"submissionId\":\"" + session + "\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("submissionId"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));
    patch("/api/entries/" + id, "{\"authorType\":\"patient\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("authorType"));
    assertThat(
            jdbc.queryForObject(
                "select body from entry_version where entry_id = ?::uuid", String.class, id))
        .isEqualTo("x");
    assertThat(
            jdbc.queryForObject(
                "select submission_id::text from entry where id = ?::uuid", String.class, id))
        .isNull();
  }

  @Test
  void linksAreRefusedOnPatch() throws Exception {
    // M2-02 : les liens se posent à la création ; ensuite par /api/entries/{id}/links.
    String id = createEntry(patient, "{\"type\":\"note\",\"body\":\"x\"}");
    patch(
            "/api/entries/" + id,
            """
            {"links":[{"targetId":"%s","linkType":"triggers","direction":"incoming"}]}
            """
                .formatted(session))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("links"))
        .andExpect(jsonPath("$.errors[0].code").value("NOT_SUPPORTED_ON_PATCH"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("/links")));
  }

  @Test
  void forbiddenFieldsAreAllReportedAtOnce() throws Exception {
    // links est accepté à la création (M2-02) : seuls authorType et submissionId sont refusés.
    create(
            "{\"type\":\"note\",\"body\":\"x\",\"authorType\":\"a\",\"submissionId\":\"b\",\"links\":[]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(
            jsonPath("$.errors[*].field").value(containsInAnyOrder("authorType", "submissionId")));
    create("{\"type\":\"note\",\"body\":\"x\",\"links\":[]}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links.length()").value(0));
  }

  // ---- modification

  @Test
  void patchChecksTheMergedEntryAndReportsEveryField() throws Exception {
    String id =
        createEntry(
            patient,
            "{\"type\":\"feeling\",\"feeling\":{\"emotionCode\":\"anxiety\",\"intensity\":4}}");
    patch("/api/entries/" + id, "{\"feeling\":{\"emotionCode\":\"nope\",\"intensity\":11}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(
            jsonPath("$.errors[*].code")
                .value(containsInAnyOrder("OUT_OF_RANGE", "UNKNOWN_EMOTION")));
    patch("/api/entries/" + id, "{\"medication\":{\"molecule\":\"x\"}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("medication"))
        .andExpect(jsonPath("$.errors[0].code").value("DETAIL_NOT_ALLOWED"));
    patch("/api/entries/" + id, "{\"tags\":[\"inconnu\"]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("tags[0]"));
  }

  @Test
  void anEventCannotLoseItsTitle() throws Exception {
    String id = createEntry(patient, "{\"type\":\"event\",\"title\":\"Titre\"}");
    patch("/api/entries/" + id, "{\"title\":\" \"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("title"));
  }

  // ---- requêtes de séance

  @Test
  void sessionRequestsAreValidatedToo() throws Exception {
    post("/api/patients/" + patient + "/sessions/start", "{\"modality\":\"smoke_signal\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("modality"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    post("/api/patients/" + patient + "/sessions/start", "{}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    post("/api/sessions/" + session + "/close", "{\"durationMin\":0}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("durationMin"))
        .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));
    post(
            "/api/patients/" + patient + "/sessions/contact",
            "{\"modality\":\"video\",\"body\":\" \"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(containsInAnyOrder("modality", "occurredFrom", "body")));
    post(
            "/api/patients/" + patient + "/sessions/start",
            "{\"modality\":\"video\",\"authorType\":\"x\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("authorType"))
        .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));
  }
}
