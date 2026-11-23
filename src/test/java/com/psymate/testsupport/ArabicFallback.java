package com.psymate.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.shared.i18n.ServedLanguage;
import java.nio.charset.StandardCharsets;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Une même requête envoyée avec {@code Accept-Language: fr} puis {@code Accept-Language: ar}
 * (M16-02, G Q96) : l'arabe ne provoque aucune erreur et reçoit le repli français tant que {@code
 * messages_ar.properties} n'existe pas (M16-04). Une fois ce fichier livré, seuls le statut et le
 * type de contenu sont comparés : le corps est alors en arabe. Le résumé « En bref » et l'export
 * restent en français dans tous les cas (G Q102, {@link #assertAlwaysFrench}).
 */
public final class ArabicFallback {

  /** En-tête d'une requête en arabe, avec un pays, comme l'envoie un navigateur. */
  public static final String ARABIC = "ar-MA,ar;q=0.9";

  private ArabicFallback() {}

  /** Vrai tant que l'arabe reçoit les messages français (aucun {@code messages_ar.properties}). */
  public static boolean frenchFallback() {
    return ServedLanguage.AR.messageLocale().equals(ServedLanguage.FR.locale());
  }

  /**
   * Même statut (jamais 5xx), même type de contenu et, tant que le repli s'applique, même corps.
   */
  public static void assertArabicGetsFrench(ResultActions french, ResultActions arabic)
      throws Exception {
    MvcResult fr = french.andReturn();
    MvcResult ar = arabic.andReturn();
    sameStatusAndType(fr, ar);
    if (frenchFallback()) {
      assertThat(body(ar)).as("réponse en arabe = réponse en français").isEqualTo(body(fr));
    }
  }

  /** Même statut, même type de contenu, même corps, quels que soient les fichiers de messages. */
  public static void assertAlwaysFrench(ResultActions french, ResultActions arabic)
      throws Exception {
    MvcResult fr = french.andReturn();
    MvcResult ar = arabic.andReturn();
    sameStatusAndType(fr, ar);
    assertThat(ar.getResponse().getContentAsByteArray())
        .as("réponse en arabe = réponse en français (G Q102)")
        .isEqualTo(fr.getResponse().getContentAsByteArray());
  }

  private static void sameStatusAndType(MvcResult fr, MvcResult ar) {
    MockHttpServletResponse arabic = ar.getResponse();
    assertThat(arabic.getStatus()).as("statut en arabe").isLessThan(500);
    assertThat(arabic.getStatus()).as("statut en arabe").isEqualTo(fr.getResponse().getStatus());
    assertThat(arabic.getContentType())
        .as("type de contenu en arabe")
        .isEqualTo(fr.getResponse().getContentType());
  }

  private static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }
}
