package com.psymate.identity.auth.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertArabicGetsFrench;
import static com.psymate.testsupport.ArabicFallback.frenchFallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.auth.AuthTestConfig;
import com.psymate.identity.auth.RegistrationFixtures;
import com.psymate.testsupport.MailpitClient;
import com.psymate.testsupport.SharedMailpit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * M16-02 (G Q96, Q97) : inscription avec {@code Accept-Language: ar}. Le compte est créé sans
 * erreur, {@code practitioner.locale} vaut {@code ar} (V16), et l'e-mail de confirmation part en
 * français tant que l'arabe n'est pas traduit. Même configuration que RegistrationIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({AuthTestConfig.class, RegistrationIT.ListenerConfig.class})
class RegistrationArabicIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  private final JsonMapper json = JsonMapper.builder().build();

  private static String newEmail() {
    return "ar" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
  }

  private ResultActions register(Map<String, Object> payload, String language) throws Exception {
    var request =
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(payload));
    if (language != null) {
      request.header("Accept-Language", language);
    }
    return mvc.perform(request);
  }

  private String localeOf(String email) {
    return jdbc.queryForObject(
        "select locale from practitioner where email_normalized = ?", String.class, email);
  }

  @Test
  void arabicRegistrationStoresArAndSendsTheConfirmationEmail() throws Exception {
    String email = newEmail();
    register(
            RegistrationFixtures.payload(email, RegistrationFixtures.PASSWORD, "psychologist"),
            ARABIC)
        .andExpect(status().isCreated());
    assertThat(localeOf(email)).isEqualTo("ar");

    MailpitClient mails = new MailpitClient(SharedMailpit.container());
    if (frenchFallback()) {
      // Objet français (« PsyMate : confirmez votre adresse e-mail ») : repli écrit, sans erreur.
      String text = mails.lastText(email, "confirmez");
      assertThat(text).isNotNull().contains("/confirmer-email/");
    } else {
      assertThat(mails.awaitMessage(email)).isNotNull();
    }
  }

  @Test
  void frenchEnglishAndUnservedLanguagesAreStoredAsBefore() throws Exception {
    String english = newEmail();
    register(RegistrationFixtures.payload(english, RegistrationFixtures.PASSWORD, "coach"), "en-GB")
        .andExpect(status().isCreated());
    assertThat(localeOf(english)).isEqualTo("en");

    String german = newEmail();
    register(RegistrationFixtures.payload(german, RegistrationFixtures.PASSWORD, "coach"), "de")
        .andExpect(status().isCreated());
    assertThat(localeOf(german)).isEqualTo("fr");

    String none = newEmail();
    register(RegistrationFixtures.payload(none, RegistrationFixtures.PASSWORD, "coach"), null)
        .andExpect(status().isCreated());
    assertThat(localeOf(none)).isEqualTo("fr");
  }

  /** Erreurs de l'inscription : règles du mot de passe et messages par défaut de la validation. */
  @Test
  void arabicRegistrationErrorsGetTheFrenchMessages() throws Exception {
    Map<String, Object> weak = RegistrationFixtures.payload(newEmail(), "court", "psychologist");
    assertArabicGetsFrench(
        register(weak, "fr").andExpect(status().isUnprocessableContent()), register(weak, ARABIC));

    Map<String, Object> tooLong =
        RegistrationFixtures.payload(newEmail(), RegistrationFixtures.PASSWORD, "psychologist");
    tooLong.put("lastName", "x".repeat(101));
    assertArabicGetsFrench(
        register(tooLong, "fr")
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.errors.length()").value(1)),
        register(tooLong, ARABIC));
  }
}
