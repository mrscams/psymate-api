package com.psymate.clinical.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertArabicGetsFrench;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * M16-02 (G Q96) : entrées et timeline demandées en arabe. Les erreurs (ClinicalMessages) reçoivent
 * le français tant que l'arabe n'est pas traduit, sans erreur. Même configuration que ValidationIT.
 */
class ClinicalArabicIT extends ClinicalWebSupport {

  private String event;

  @BeforeEach
  void openSession() throws Exception {
    startSession(patient);
    event = createEntry(patient, "{\"type\":\"event\",\"title\":\"Dispute\"}");
  }

  private ResultActions in(String language, Supplier<MockHttpServletRequestBuilder> request)
      throws Exception {
    return mvc.perform(request.get().header("Accept-Language", language).with(me));
  }

  private Supplier<MockHttpServletRequestBuilder> postEntry(String json) {
    return () ->
        MockMvcRequestBuilders.post(entriesUrl(patient))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json);
  }

  @Test
  void arabicGetsTheFrenchValidationMessages() throws Exception {
    Supplier<MockHttpServletRequestBuilder> unknownEmotion =
        postEntry("{\"type\":\"feeling\",\"feeling\":{\"emotionCode\":\"nope\",\"intensity\":3}}");
    assertArabicGetsFrench(
        in("fr", unknownEmotion)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value("Cette émotion n'existe pas dans votre catalogue.")),
        in(ARABIC, unknownEmotion));
    Supplier<MockHttpServletRequestBuilder> noBody = postEntry("{\"type\":\"note\"}");
    assertArabicGetsFrench(
        in("fr", noBody).andExpect(status().isUnprocessableEntity()), in(ARABIC, noBody));
    Supplier<MockHttpServletRequestBuilder> malformed = postEntry("{pas du json");
    assertArabicGetsFrench(
        in("fr", malformed).andExpect(status().isBadRequest()), in(ARABIC, malformed));
  }

  @Test
  void arabicTimelineErrorsGetTheFrenchMessages() throws Exception {
    Supplier<MockHttpServletRequestBuilder> tooMany =
        () -> MockMvcRequestBuilders.get("/api/patients/" + patient + "/timeline?limit=201");
    assertArabicGetsFrench(
        in("fr", tooMany).andExpect(status().is4xxClientError()), in(ARABIC, tooMany));
    Supplier<MockHttpServletRequestBuilder> timeline =
        () -> MockMvcRequestBuilders.get("/api/patients/" + patient + "/timeline");
    in(ARABIC, timeline).andExpect(status().isOk());
  }

  /** Lien posé avec l'entrée, en arabe : sans erreur, avec un libellé (EntryLinkAssembler). */
  @Test
  void arabicEntryWithLinksIsCreatedWithALabel() throws Exception {
    String json =
        "{\"type\":\"feeling\",\"feeling\":{\"emotionCode\":\"anxiety\",\"intensity\":6},"
            + "\"links\":[{\"targetId\":\""
            + event
            + "\",\"linkType\":\"triggers\",\"direction\":\"incoming\"}]}";
    in(ARABIC, postEntry(json))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links[0].label").isNotEmpty());
  }
}
