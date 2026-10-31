package com.psymate.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Endpoints entrées (M1-05) : création par type, lecture, correction, versions, archivage. */
class EntryControllerIT extends ClinicalWebSupport {

  private String session;

  @BeforeEach
  void openSession() throws Exception {
    session = startSession(patient);
  }

  private String entry(String id) {
    return "/api/entries/" + id;
  }

  // ---- création par type

  @Test
  void a5PayloadWithoutLinksIsAcceptedWithVersion1AndCopiedAffect() throws Exception {
    // Valence et activation du vocabulaire (NULL dans le seed V1) : on les renseigne pour ce test.
    jdbc.update("update emotion set valence = -2, arousal = 3 where code = 'anxiety'");
    try {
      createTag("travail");
      String payload =
          """
          {
            "type": "feeling",
            "status": "final",
            "occurred": { "from": "2026-10-20T19:00:00Z", "precision": "exact" },
            "sessionId": "%s",
            "visibility": "clinical",
            "body": "Monte avant les appels du soir",
            "tags": ["travail"],
            "feeling": { "emotionCode": "anxiety", "intensity": 8 }
          }
          """
              .formatted(session);

      var result =
          post(entriesUrl(patient), payload)
              .andExpect(status().isCreated())
              .andExpect(header().string("Location", containsString("/api/entries/")))
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$.type").value("feeling"))
              .andExpect(jsonPath("$.status").value("final"))
              .andExpect(jsonPath("$.visibility").value("clinical"))
              .andExpect(jsonPath("$.patientId").value(patient.toString()))
              .andExpect(jsonPath("$.sessionId").value(session))
              .andExpect(jsonPath("$.version").value(1))
              .andExpect(jsonPath("$.authorType").value("practitioner"))
              .andExpect(jsonPath("$.body").value("Monte avant les appels du soir"))
              .andExpect(jsonPath("$.occurred.from").value("2026-10-20T19:00:00Z"))
              .andExpect(jsonPath("$.occurred.to").value("2026-10-20T19:00:00Z"))
              .andExpect(jsonPath("$.occurred.precision").value("exact"))
              .andExpect(jsonPath("$.feeling.emotionCode").value("anxiety"))
              .andExpect(jsonPath("$.feeling.family").value("fear"))
              .andExpect(jsonPath("$.feeling.intensity").value(8))
              .andExpect(jsonPath("$.feeling.valence").value(-2))
              .andExpect(jsonPath("$.feeling.arousal").value(3))
              .andExpect(jsonPath("$.tags[0]").value("travail"))
              .andExpect(jsonPath("$.linkCount").value(0))
              .andExpect(jsonPath("$.recordedAt").exists())
              .andExpect(jsonPath("$.archivedAt").value(nullValue()))
              .andExpect(jsonPath("$.medication").value(nullValue()))
              .andExpect(jsonPath("$.session").value(nullValue()));

      String id = idOf(result);
      assertThat(count("select count(*) from entry_version where entry_id = ?::uuid", id))
          .isEqualTo(1);
      assertThat(count("select count(*) from entry_tag where entry_id = ?::uuid", id)).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "select author_type from entry where id = ?::uuid", String.class, id))
          .isEqualTo("practitioner");
    } finally {
      jdbc.update("update emotion set valence = null, arousal = null where code = 'anxiety'");
    }
  }

  @Test
  void feelingAffectIsNullWhenTheVocabularyHasNone() throws Exception {
    post(
            entriesUrl(patient),
            "{\"type\":\"feeling\",\"feeling\":{\"emotionCode\":\"anxiety\",\"intensity\":4}}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.feeling.valence").value(nullValue()))
        .andExpect(jsonPath("$.feeling.arousal").value(nullValue()))
        .andExpect(jsonPath("$.feeling.family").value("fear"));
  }

  @Test
  void entryWithoutSessionIdIsAttachedToTheCurrentSession() throws Exception {
    post(entriesUrl(patient), "{\"type\":\"note\",\"body\":\"Une note\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("note"))
        .andExpect(jsonPath("$.body").value("Une note"))
        .andExpect(jsonPath("$.sessionId").value(session))
        .andExpect(jsonPath("$.authorType").value("practitioner"))
        .andExpect(jsonPath("$.occurred.precision").value("exact"))
        .andExpect(jsonPath("$.feeling").value(nullValue()))
        .andExpect(jsonPath("$.tags.length()").value(0));
  }

  @Test
  void eventIsCreatedWithItsTitle() throws Exception {
    post(
            entriesUrl(patient),
            "{\"type\":\"event\",\"title\":\"Décès du père\",\"visibility\":\"private\","
                + "\"occurred\":{\"from\":\"2019-05-04T00:00:00Z\",\"precision\":\"day\"}}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("event"))
        .andExpect(jsonPath("$.title").value("Décès du père"))
        .andExpect(jsonPath("$.visibility").value("private"))
        .andExpect(jsonPath("$.occurred.precision").value("day"))
        .andExpect(jsonPath("$.occurred.from").value("2019-05-04T00:00:00Z"))
        .andExpect(jsonPath("$.occurred.to").value("2019-05-04T23:59:59Z"));
  }

  @Test
  void situationWithMonthPrecisionAndNoEndIsOngoing() throws Exception {
    post(
            entriesUrl(patient),
            """
            {"type":"situation","title":"Surcharge au travail",
             "occurred":{"from":"2026-03-15T10:00:00Z","precision":"month","label":"depuis mars"}}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("situation"))
        .andExpect(jsonPath("$.occurred.precision").value("month"))
        .andExpect(jsonPath("$.occurred.from").value("2026-03-01T00:00:00Z"))
        .andExpect(jsonPath("$.occurred.to").value(nullValue()))
        .andExpect(jsonPath("$.occurred.label").value("depuis mars"));
  }

  @Test
  void medicationIsCreatedWithItsDetail() throws Exception {
    post(
            entriesUrl(patient),
            """
            {"type":"medication",
             "medication":{"molecule":"sertraline","dosage":"50 mg","frequency":"matin",
                           "prescriber":"Dr X","periodFrom":"2026-01-05","periodTo":null}}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("medication"))
        .andExpect(jsonPath("$.medication.molecule").value("sertraline"))
        .andExpect(jsonPath("$.medication.dosage").value("50 mg"))
        .andExpect(jsonPath("$.medication.frequency").value("matin"))
        .andExpect(jsonPath("$.medication.prescriber").value("Dr X"))
        .andExpect(jsonPath("$.medication.periodFrom").value("2026-01-05"))
        .andExpect(jsonPath("$.medication.periodTo").value(nullValue()))
        .andExpect(jsonPath("$.authorType").value("practitioner"));
  }

  @Test
  void aSessionBodyIsRoutedToStartAndKeepsTheOpenSessionLock() throws Exception {
    // Une séance est déjà ouverte (setUp) : si le corps passait par EntryService.create, une
    // seconde séance ouverte serait créée. Routé vers SessionService.start, il reçoit un 409.
    post(entriesUrl(patient), "{\"type\":\"session\",\"session\":{\"modality\":\"video\"}}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ALREADY_OPEN"));
    assertThat(
            count(
                "select count(*) from entry e join entry_session s on s.entry_id = e.id"
                    + " where e.patient_id = ? and s.closed_at is null",
                patient))
        .isEqualTo(1);

    post("/api/sessions/" + session + "/close", "{\"durationMin\":50}").andExpect(status().isOk());
    post(entriesUrl(patient), "{\"type\":\"session\",\"session\":{\"modality\":\"video\"}}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("session"))
        .andExpect(jsonPath("$.session.kind").value("session"))
        .andExpect(jsonPath("$.session.modality").value("video"))
        .andExpect(jsonPath("$.session.closedAt").value(nullValue()))
        .andExpect(jsonPath("$.sessionId").value(nullValue()))
        .andExpect(jsonPath("$.authorType").value("practitioner"));
  }

  @Test
  void aSessionBodyWithADurationIsRefusedWithAPointerToClose() throws Exception {
    post(
            entriesUrl(patient),
            "{\"type\":\"session\",\"session\":{\"modality\":\"video\",\"durationMin\":50}}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("session.durationMin"))
        .andExpect(jsonPath("$.errors[0].code").value("NOT_ALLOWED_ON_CREATE"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("/close")));
  }

  @Test
  void aSessionBodyWithContentIsRefusedRatherThanSilentlyDropped() throws Exception {
    int before = count("select count(*) from entry where patient_id = ?", patient);
    post(
            entriesUrl(patient),
            "{\"type\":\"session\",\"session\":{\"modality\":\"video\"},"
                + "\"title\":\"t\",\"body\":\"b\",\"tags\":[\"travail\"]}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("title", "body", "tags")))
        .andExpect(jsonPath("$.errors[0].code").value("NOT_ALLOWED_ON_CREATE"));
    assertThat(count("select count(*) from entry where patient_id = ?", patient))
        .as("aucune séance démarrée")
        .isEqualTo(before);
  }

  // ---- lecture

  @Test
  void getReturnsTheFullEntryWithAuthorType() throws Exception {
    String id =
        createEntry(
            patient,
            "{\"type\":\"feeling\",\"feeling\":{\"emotionCode\":\"anxiety\",\"intensity\":6}}");
    get(entry(id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.type").value("feeling"))
        .andExpect(jsonPath("$.feeling.intensity").value(6))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.authorType").value("practitioner"));
  }

  @Test
  void getUnknownEntryIs404() throws Exception {
    get(entry(UUID.randomUUID().toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENTRY_NOT_FOUND"));
  }

  // ---- correction : grâce et versions

  @Test
  void patchTwoMinutesAfterCreationByTheSameAuthorKeepsVersion1() throws Exception {
    String id = createEntry(patient, "{\"type\":\"note\",\"body\":\"Première version\"}");
    clock.advance(Duration.ofMinutes(2));

    patch(entry(id), "{\"body\":\"Texte corrigé\",\"reason\":\"faute de frappe\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.body").value("Texte corrigé"))
        .andExpect(jsonPath("$.authorType").value("practitioner"));
    assertThat(count("select count(*) from entry_version where entry_id = ?::uuid", id))
        .isEqualTo(1);
  }

  @Test
  void patchAfterTheGraceWindowCreatesVersion2() throws Exception {
    String id =
        createEntry(
            patient,
            "{\"type\":\"feeling\",\"feeling\":{\"emotionCode\":\"anxiety\",\"intensity\":8}}");
    clock.advance(Duration.ofMinutes(76));

    patch(entry(id), "{\"feeling\":{\"intensity\":5},\"reason\":\"réévalué\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.feeling.intensity").value(5))
        .andExpect(jsonPath("$.feeling.emotionCode").value("anxiety"))
        .andExpect(jsonPath("$.authorType").value("practitioner"));
    assertThat(count("select count(*) from entry_version where entry_id = ?::uuid", id))
        .isEqualTo(2);
  }

  @Test
  void versionsAreListedNewestFirstWithAuthorType() throws Exception {
    String id = createEntry(patient, "{\"type\":\"note\",\"body\":\"v1\"}");
    clock.advance(Duration.ofMinutes(80));
    patch(entry(id), "{\"body\":\"v2\",\"reason\":\"précision\"}").andExpect(status().isOk());

    get(entry(id) + "/versions")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].version").value(2))
        .andExpect(jsonPath("$[0].body").value("v2"))
        .andExpect(jsonPath("$[0].reason").value("précision"))
        .andExpect(jsonPath("$[0].authorType").value("practitioner"))
        .andExpect(jsonPath("$[0].createdAt").exists())
        .andExpect(jsonPath("$[1].version").value(1))
        .andExpect(jsonPath("$[1].body").value("v1"))
        .andExpect(jsonPath("$[1].authorType").value("practitioner"));
  }

  @Test
  void patchWithNothingToChangeKeepsTheVersion() throws Exception {
    String id = createEntry(patient, "{\"type\":\"note\",\"body\":\"Identique\"}");
    clock.advance(Duration.ofMinutes(80));
    patch(entry(id), "{\"body\":\"Identique\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
  }

  // ---- tags

  @Test
  void aTagsOnlyPatchReplacesTheTagsWithoutCreatingAVersion() throws Exception {
    createTag("travail");
    createTag("famille");
    String id = createEntry(patient, "{\"type\":\"note\",\"body\":\"n\",\"tags\":[\"travail\"]}");
    clock.advance(Duration.ofMinutes(80));

    patch(entry(id), "{\"tags\":[\"famille\"]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.tags.length()").value(1))
        .andExpect(jsonPath("$.tags[0]").value("famille"));
    assertThat(count("select count(*) from entry_version where entry_id = ?::uuid", id))
        .isEqualTo(1);

    patch(entry(id), "{\"tags\":[]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tags.length()").value(0));
    assertThat(count("select count(*) from entry_tag where entry_id = ?::uuid", id)).isZero();
  }

  // ---- archivage

  @Test
  void deleteArchivesAndRestoreBringsTheEntryBack() throws Exception {
    String id = createEntry(patient, "{\"type\":\"note\",\"body\":\"À archiver\"}");

    delete(entry(id)).andExpect(status().isNoContent());
    get(entry(id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty())
        .andExpect(jsonPath("$.authorType").value("practitioner"));
    assertThat(count("select count(*) from entry where id = ?::uuid", id))
        .as("jamais de suppression physique")
        .isEqualTo(1);
    patch(entry(id), "{\"body\":\"x\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ENTRY_ARCHIVED"));

    post(entry(id) + "/restore", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").value(nullValue()))
        .andExpect(jsonPath("$.authorType").value("practitioner"));
    get(entry(id)).andExpect(jsonPath("$.archivedAt").value(nullValue()));
  }

  // ---- authorType et propriété

  @Test
  void authorTypeIsPractitionerInEveryEntryResponse() throws Exception {
    String id =
        idOf(
            post(entriesUrl(patient), "{\"type\":\"note\",\"body\":\"n\"}")
                .andExpect(jsonPath("$.authorType").value("practitioner")));
    get(entry(id)).andExpect(jsonPath("$.authorType").value("practitioner"));
    patch(entry(id), "{\"body\":\"m\"}").andExpect(jsonPath("$.authorType").value("practitioner"));
    get(entry(id) + "/versions").andExpect(jsonPath("$[0].authorType").value("practitioner"));
    delete(entry(id)).andExpect(status().isNoContent());
    post(entry(id) + "/restore", null).andExpect(jsonPath("$.authorType").value("practitioner"));
    get("/api/patients/" + patient + "/sessions/current")
        .andExpect(jsonPath("$.authorType").value("practitioner"));
  }

  @Test
  void anotherPractitionersPatientIsForbiddenNotHidden() throws Exception {
    String id = createEntry(patient, "{\"type\":\"note\",\"body\":\"Secret\"}");
    RequestPostProcessor other = as(fx.newPractitioner());

    sendGet(entry(id), other)
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    sendGet(entry(id) + "/versions", other).andExpect(status().isForbidden());
    send(MockMvcRequestBuilders.patch(entry(id)), "{\"body\":\"piraté\"}", other)
        .andExpect(status().isForbidden());
    send(MockMvcRequestBuilders.delete(entry(id)), null, other).andExpect(status().isForbidden());
    send(MockMvcRequestBuilders.post(entry(id) + "/restore"), null, other)
        .andExpect(status().isForbidden());
    send(
            MockMvcRequestBuilders.post(entriesUrl(patient)),
            "{\"type\":\"note\",\"body\":\"intrus\"}",
            other)
        .andExpect(status().isForbidden());

    assertThat(
            jdbc.queryForObject(
                "select body from entry_version where entry_id = ?::uuid", String.class, id))
        .isEqualTo("Secret");
    assertThat(count("select count(*) from entry where patient_id = ?", patient))
        .as("la séance et la note seulement")
        .isEqualTo(2);
  }

  @Test
  void createdLocationPointsToTheEntry() throws Exception {
    var result =
        post(entriesUrl(patient), "{\"type\":\"note\",\"body\":\"n\"}")
            .andExpect(status().isCreated());
    String id = idOf(result);
    result.andExpect(header().string("Location", "/api/entries/" + id));
  }

  private ResultActions sendGet(String url, RequestPostProcessor auth) throws Exception {
    return send(MockMvcRequestBuilders.get(url), null, auth);
  }
}
