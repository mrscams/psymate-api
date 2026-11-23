package com.psymate.search.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertArabicGetsFrench;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.search.SearchSupport;
import com.psymate.testsupport.SharedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * M16-02 (G Q96) : recherche demandée en arabe. La recherche ne dépend d'aucune langue (G Q102) ;
 * ses erreurs reçoivent le français tant que l'arabe n'est pas traduit. Même configuration que
 * SearchIT.
 */
@Import(SharedPostgres.class)
class SearchArabicIT extends SearchSupport {

  private ResultActions search(String language, String name, String value) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.get("/api/patients/{id}/search", patient)
            .queryParam(name, value)
            .header("Accept-Language", language)
            .with(auth));
  }

  @Test
  void arabicGetsTheFrenchErrorMessages() throws Exception {
    String[][] cases = {
      {"page", "0"}, {"page", "abc"}, {"q", "a".repeat(201)}, {"sessionId", "pas-un-uuid"}
    };
    for (String[] c : cases) {
      assertArabicGetsFrench(
          search("fr", c[0], c[1]).andExpect(status().isBadRequest()), search(ARABIC, c[0], c[1]));
    }
  }

  /** Même réponse, durée de la requête ({@code tookMs}) mise à part. */
  @Test
  void anArabicSearchAnswersLikeAFrenchOne() throws Exception {
    JsonMapper json = JsonMapper.builder().build();
    ObjectNode french =
        (ObjectNode) json.readTree(body(search("fr", "q", "dispute").andExpect(status().isOk())));
    ObjectNode arabic =
        (ObjectNode) json.readTree(body(search(ARABIC, "q", "dispute").andExpect(status().isOk())));
    french.remove("tookMs");
    arabic.remove("tookMs");
    assertThat(arabic).isEqualTo(french);
  }
}
