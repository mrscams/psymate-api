package com.psymate.identity.preferences;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertArabicGetsFrench;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.util.List;
import java.util.UUID;
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
 * M16-02 (G Q97) : la préférence {@code locale} accepte {@code ar} ; toute autre valeur reste
 * refusée comme avant, avec un message français quand l'arabe est demandé (G Q96). Même
 * configuration que PreferencesIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, PreferencesIT.ListenerConfig.class})
class PreferencesArabicIT {

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

  private String stored(UUID id) {
    return jdbc.queryForObject(
        "select preferences::text from practitioner where id = ?", String.class, id);
  }

  @Test
  void localeArIsAcceptedAndReadBack() throws Exception {
    UUID id = insertPractitioner();
    patchJson(id, "{\"locale\":\"ar\"}", ARABIC)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locale").value("ar"));
    mvc.perform(get("/api/me/preferences").header("Accept-Language", ARABIC).with(as(id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locale").value("ar"));
    assertThat(stored(id)).contains("\"locale\": \"ar\"");
  }

  @Test
  void otherLocalesAreStillRefused() throws Exception {
    UUID id = insertPractitioner();
    for (String value : List.of("\"de\"", "\"AR\"", "\"ar-MA\"", "\"\"", "12")) {
      patchJson(id, "{\"locale\":" + value + "}", "fr")
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.errors[0].field").value("locale"))
          .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"));
    }
    assertThat(stored(id)).doesNotContain("locale");
  }

  @Test
  void arabicGetsTheFrenchValidationMessages() throws Exception {
    UUID id = insertPractitioner();
    assertArabicGetsFrench(
        patchJson(id, "{\"locale\":\"de\"}", "fr")
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.detail").value("Certaines préférences sont invalides.")),
        patchJson(id, "{\"locale\":\"de\"}", ARABIC));
  }

  @Test
  void theContractListsFrEnAr() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.components.schemas.Preferences.properties.locale.enum")
                .value(contains("fr", "en", "ar")));
  }
}
