package com.psymate.identity.preferences;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * M16-04 (G Q99, Q101) : une erreur de validation demandée avec {@code Accept-Language: ar} revient
 * en arabe, {@code detail} et message par champ, avec des chiffres latins ; le français ne change
 * pas. Même configuration que PreferencesIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, PreferencesIT.ListenerConfig.class})
class PreferencesArabicMessagesIT {

  private static final Pattern ARABIC_LETTER = Pattern.compile("[\\u0621-\\u064A]");
  private static final Pattern ARABIC_INDIC_DIGIT =
      Pattern.compile("[\\u0660-\\u0669\\u06F0-\\u06F9]");

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  private UUID insertPractitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  private RequestPostProcessor as(UUID id) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            id, tenant, id + "@example.test", "Dr Test", "psychologist", "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions patchJson(UUID id, String body, String language) throws Exception {
    return mvc.perform(
        patch("/api/me/preferences")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Accept-Language", language)
            .content(body)
            .with(as(id)));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private static void assertArabicWithLatinDigits(String text, String what) {
    assertThat(text).as(what).isNotBlank();
    assertThat(ARABIC_LETTER.matcher(text).find()).as(what + " en arabe : " + text).isTrue();
    assertThat(ARABIC_INDIC_DIGIT.matcher(text).find())
        .as(what + " sans chiffre arabo-indien : " + text)
        .isFalse();
  }

  @Test
  void invalidLocaleInArabicIsExplainedInArabic() throws Exception {
    UUID id = insertPractitioner();
    String json =
        body(
            patchJson(id, "{\"locale\":\"de\"}", ARABIC)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("locale"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE")));

    assertArabicWithLatinDigits(JsonPath.read(json, "$.detail"), "detail");
    String message = JsonPath.read(json, "$.errors[0].message");
    assertArabicWithLatinDigits(message, "message du champ locale");
    // Les valeurs techniques restent en caractères latins, ar compris (M16-02).
    assertThat(message).contains("fr", "en", "ar");
  }

  @Test
  void numbersInAnArabicFieldMessageStayInLatinDigits() throws Exception {
    UUID id = insertPractitioner();
    String json =
        body(
            patchJson(id, "{\"sessions.defaultDurationMin\":200}", ARABIC)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("sessions.defaultDurationMin")));

    assertArabicWithLatinDigits(JsonPath.read(json, "$.detail"), "detail");
    String message = JsonPath.read(json, "$.errors[0].message");
    assertArabicWithLatinDigits(message, "message du champ sessions.defaultDurationMin");
    assertThat(message).contains("15", "180");
  }

  @Test
  void frenchAndEnglishAreUnchanged() throws Exception {
    UUID id = insertPractitioner();
    patchJson(id, "{\"locale\":\"de\"}", "fr")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.detail").value("Certaines préférences sont invalides."))
        .andExpect(jsonPath("$.errors[0].message").value("Valeurs admises : fr, en."));
    String english = body(patchJson(id, "{\"locale\":\"de\"}", "en"));
    assertThat(ARABIC_LETTER.matcher(english).find()).as("réponse anglaise").isFalse();
  }
}
