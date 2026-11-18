package com.psymate.patients.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.shared.problem.FieldProblem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Messages du Profil 360 : clés présentes en français et en anglais, apostrophes conservées. */
class PatientProfileMessagesTest {

  private final PatientProfileMessages messages = new PatientProfileMessages();

  @AfterEach
  void clear() {
    RequestContextHolder.resetRequestAttributes();
  }

  private static void acceptLanguage(String header) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Accept-Language", header);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @Test
  void frenchByDefaultWithApostrophesKept() {
    assertThat(messages.field(new FieldProblem("periodLabel", "INVALID_VALUE")).message())
        .isEqualTo(
            "Une période n'est admise que dans la rubrique Histoire et souvenirs (history).");
    assertThat(messages.field(new FieldProblem("title", "REQUIRED")).message())
        .isEqualTo("L'information est obligatoire.");
    assertThat(messages.domain("PROFILE_ITEM_LIMIT_REACHED", 500))
        .isEqualTo(
            "Le profil compte déjà 500 informations, le maximum : retirez-en une avant d'en"
                + " ajouter.");
    assertThat(messages.domain("PROFILE_ITEM_ARCHIVED")).contains("retirée du profil");
  }

  @Test
  void aFieldWithoutItsOwnKeyFallsBackToTheCodeMessage() {
    assertThat(messages.field(new FieldProblem("authorType", "UNKNOWN_FIELD")).message())
        .isEqualTo("Champ inconnu ou non modifiable.");
    assertThat(messages.field(new FieldProblem("x", "NO_SUCH_CODE")).message())
        .isEqualTo("NO_SUCH_CODE");
  }

  @Test
  void englishWhenAskedFirst() {
    acceptLanguage("en-GB,fr;q=0.5");
    assertThat(messages.domain("PROFILE_ITEM_NOT_FOUND"))
        .isEqualTo("Profile information not found for this patient.");
    assertThat(messages.field(new FieldProblem("periodYear", "OUT_OF_RANGE")).message())
        .isEqualTo("The year must be between 1900 and 2100.");
    acceptLanguage("fr-FR");
    assertThat(messages.domain("PROFILE_ITEM_NOT_FOUND"))
        .isEqualTo("Information de profil introuvable pour ce patient.");
  }
}
