package com.psymate.clinical.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * M16-03 (G Q98) : liens posés à la création d'une entrée (EntryLinkAssembler). En arabe, le verbe
 * est le libellé arabe du type, inverse quand la nouvelle entrée est la cible, le français à défaut
 * de libellé arabe ; en français et en anglais, il ne change pas.
 */
class EntryLinkArabicLabelIT extends ClinicalWebSupport {

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

  /**
   * Un ressenti lié à l'événement (triggers, la nouvelle entrée est la cible) et à la situation.
   */
  private ResultActions feeling(String language) throws Exception {
    String json =
        "{\"type\":\"feeling\",\"feeling\":{\"emotionCode\":\"anxiety\",\"intensity\":6},"
            + "\"links\":[{\"targetId\":\""
            + event
            + "\",\"linkType\":\"triggers\",\"direction\":\"incoming\"},{\"targetId\":\""
            + situation
            + "\",\"linkType\":\"co_occurs\"}]}";
    return mvc.perform(
        MockMvcRequestBuilders.post(entriesUrl(patient))
            .header("Accept-Language", language)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
            .with(me));
  }

  @Test
  void linksCreatedWithAnEntryAreNamedInArabic() throws Exception {
    feeling(ARABIC)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links[0].linkType").value("triggers"))
        .andExpect(jsonPath("$.links[0].label").value("يُثيره"))
        .andExpect(jsonPath("$.links[1].linkType").value("co_occurs"))
        .andExpect(jsonPath("$.links[1].label").value("يتزامن مع"));
  }

  @Test
  void frenchAndEnglishLabelsAreUnchanged() throws Exception {
    feeling("fr")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links[0].label").value("déclenché par"))
        .andExpect(jsonPath("$.links[1].label").value("survient avec"));
    feeling("en")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.links[0].label").value("triggered by"))
        .andExpect(jsonPath("$.links[1].label").value("co-occurs with"));
  }

  @Test
  void withoutArabicLabelsTheFrenchIsServed() throws Exception {
    String inverseAr =
        jdbc.queryForObject(
            "select inverse_label_ar from link_type where code = 'triggers'", String.class);
    String coOccursAr =
        jdbc.queryForObject(
            "select label_ar from link_type where code = 'co_occurs'", String.class);
    jdbc.update("update link_type set inverse_label_ar = null where code = 'triggers'");
    jdbc.update("update link_type set label_ar = null where code = 'co_occurs'");
    try {
      feeling(ARABIC)
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.links[0].label").value("déclenché par"))
          .andExpect(jsonPath("$.links[1].label").value("survient avec"));
    } finally {
      jdbc.update("update link_type set inverse_label_ar = ? where code = 'triggers'", inverseAr);
      jdbc.update("update link_type set label_ar = ? where code = 'co_occurs'", coOccursAr);
    }
  }
}
