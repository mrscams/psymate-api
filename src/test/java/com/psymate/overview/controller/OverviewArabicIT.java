package com.psymate.overview.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertArabicGetsFrench;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.overview.OverviewSupport;
import com.psymate.testsupport.SharedPostgres;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Vue d'ensemble demandée en arabe : le résumé « En bref » sort en français, toujours (G Q102) ;
 * les erreurs reçoivent le français tant que l'arabe n'est pas traduit (G Q96). Même configuration
 * qu'OverviewNarrativeIT.
 */
@Import(SharedPostgres.class)
class OverviewArabicIT extends OverviewSupport {

  private ResultActions overviewIn(String language, String... nameValue) throws Exception {
    var get = MockMvcRequestBuilders.get("/api/patients/{id}/overview", patient);
    for (int i = 0; i < nameValue.length; i += 2) {
      get.queryParam(nameValue[i], nameValue[i + 1]);
    }
    return mvc.perform(get.header("Accept-Language", language).with(auth));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> narrative(ResultActions result) throws Exception {
    return JsonPath.read(body(result), "$.narrative");
  }

  @Test
  void theArabicNarrativeIsTheFrenchOne() throws Exception {
    for (int day : new int[] {40, 35, 31}) {
      feeling("anxiety", 3, daysAgo(day));
    }
    for (int day : new int[] {10, 5, 2}) {
      feeling("anxiety", 8, daysAgo(day));
    }
    situation("Perte d'emploi", daysAgo(20));
    medication("sertraline", "50 mg", daysAgo(15));
    // Les trois lectures portent sur la même projection : recalculs asynchrones écoulés d'abord.
    awaitOverviewQuiet();

    Map<String, Object> french = narrative(overviewIn("fr").andExpect(status().isOk()));
    Map<String, Object> arabic = narrative(overviewIn(ARABIC).andExpect(status().isOk()));
    Map<String, Object> plainArabic = narrative(overviewIn("ar").andExpect(status().isOk()));

    @SuppressWarnings("unchecked")
    List<String> sentences = (List<String>) french.get("sentences");
    assertThat(String.join(" ", sentences))
        .contains("Suivi depuis")
        .contains("Traitement en cours");
    assertThat(arabic).isEqualTo(french);
    assertThat(plainArabic).isEqualTo(french);
  }

  @Test
  void arabicGetsTheFrenchErrorMessages() throws Exception {
    assertArabicGetsFrench(
        overviewIn("fr", "window", "7d").andExpect(status().isBadRequest()),
        overviewIn(ARABIC, "window", "7d"));
  }
}
