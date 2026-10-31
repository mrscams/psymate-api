package com.psymate.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Endpoints séances (M1-05) : démarrer, clôturer, contact, séance courante. */
class SessionControllerIT extends ClinicalWebSupport {

  private String sessions(UUID patientId) {
    return "/api/patients/" + patientId + "/sessions";
  }

  @Test
  void startCreatesAnOpenSessionAndReturns201WithLocation() throws Exception {
    var result =
        post(sessions(patient) + "/start", "{\"modality\":\"video\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("session"))
            .andExpect(jsonPath("$.session.kind").value("session"))
            .andExpect(jsonPath("$.session.modality").value("video"))
            .andExpect(jsonPath("$.session.closedAt").value(nullValue()))
            .andExpect(jsonPath("$.session.durationMin").value(nullValue()))
            .andExpect(jsonPath("$.sessionId").value(nullValue()))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.authorType").value("practitioner"));
    String id = idOf(result);
    result.andExpect(header().string("Location", "/api/entries/" + id));
  }

  @Test
  void startingASecondSessionWhileOneIsOpenIs409() throws Exception {
    startSession(patient);
    post(sessions(patient) + "/start", "{\"modality\":\"in_person\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ALREADY_OPEN"));
    assertThat(
            count(
                "select count(*) from entry e join entry_session s on s.entry_id = e.id"
                    + " where e.patient_id = ? and s.closed_at is null",
                patient))
        .isEqualTo(1);
  }

  @Test
  void closeFreezesDurationAndClosedAtInAVersion() throws Exception {
    String id = startSession(patient);
    clock.advance(Duration.ofMinutes(2));

    post("/api/sessions/" + id + "/close", "{\"durationMin\":50,\"summary\":\"Bonne séance\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.session.durationMin").value(50))
        .andExpect(jsonPath("$.session.closedAt").isNotEmpty())
        .andExpect(jsonPath("$.body").value("Bonne séance"))
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.authorType").value("practitioner"));
    // Même dans la fenêtre de grâce, la clôture est une vraie version.
    get("/api/entries/" + id + "/versions")
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].reason").value("Clôture de la séance"));

    post("/api/sessions/" + id + "/close", "{\"durationMin\":50}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ALREADY_CLOSED"));
  }

  @Test
  void closingSomethingThatIsNotASessionIs422() throws Exception {
    startSession(patient);
    String note = createEntry(patient, "{\"type\":\"note\",\"body\":\"n\"}");
    post("/api/sessions/" + note + "/close", "{\"durationMin\":50}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
    post("/api/sessions/" + UUID.randomUUID() + "/close", "{}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENTRY_NOT_FOUND"));
  }

  @Test
  void contactIsRecordedAsAnAlreadyClosedSession() throws Exception {
    startSession(patient);
    post(
            sessions(patient) + "/contact",
            """
            {"modality":"phone","occurredFrom":"2026-10-22T08:30:00Z",
             "body":"Appel : le patient va mieux."}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("session"))
        .andExpect(jsonPath("$.session.kind").value("contact"))
        .andExpect(jsonPath("$.session.modality").value("phone"))
        .andExpect(jsonPath("$.session.closedAt").isNotEmpty())
        .andExpect(jsonPath("$.body").value("Appel : le patient va mieux."))
        .andExpect(jsonPath("$.occurred.from").value("2026-10-22T08:30:00Z"))
        .andExpect(jsonPath("$.authorType").value("practitioner"));
    assertThat(
            count(
                "select count(*) from entry e join entry_session s on s.entry_id = e.id"
                    + " where e.patient_id = ? and s.closed_at is null",
                patient))
        .as("la séance ouverte n'est pas touchée")
        .isEqualTo(1);
  }

  @Test
  void currentIsTheOpenSessionThenTheLastClosedOne() throws Exception {
    String first = startSession(patient);
    get(sessions(patient) + "/current")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(first))
        .andExpect(jsonPath("$.session.closedAt").value(nullValue()))
        .andExpect(jsonPath("$.authorType").value("practitioner"));

    post("/api/sessions/" + first + "/close", "{\"durationMin\":45}").andExpect(status().isOk());
    get(sessions(patient) + "/current")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(first))
        .andExpect(jsonPath("$.session.closedAt").isNotEmpty())
        .andExpect(jsonPath("$.session.durationMin").value(45));

    clock.advance(Duration.ofDays(7));
    String second = startSession(patient);
    get(sessions(patient) + "/current").andExpect(jsonPath("$.id").value(second));
  }

  @Test
  void currentWithoutAnySessionIs404() throws Exception {
    get(sessions(patient) + "/current")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NO_CURRENT_SESSION"));
  }

  @Test
  void anotherPractitionersPatientIsForbidden() throws Exception {
    String id = startSession(patient);
    var other = as(fx.newPractitioner());

    send(MockMvcRequestBuilders.get(sessions(patient) + "/current"), null, other)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    send(
            MockMvcRequestBuilders.post(sessions(patient) + "/start"),
            "{\"modality\":\"video\"}",
            other)
        .andExpect(status().isForbidden());
    send(
            MockMvcRequestBuilders.post(sessions(patient) + "/contact"),
            "{\"modality\":\"phone\",\"occurredFrom\":\"2026-10-22T08:30:00Z\",\"body\":\"x\"}",
            other)
        .andExpect(status().isForbidden());
    send(MockMvcRequestBuilders.post("/api/sessions/" + id + "/close"), "{}", other)
        .andExpect(status().isForbidden());
  }
}
