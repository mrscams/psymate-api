package com.psymate.vocabulary.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertArabicGetsFrench;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * M16-02 (G Q96) : catalogues demandés en arabe. Lectures et erreurs répondent comme en français,
 * sans erreur, tant que l'arabe n'est pas traduit. Même configuration que VocabularyIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class VocabularyArabicIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private RequestPostProcessor auth;

  @BeforeEach
  void practitioner() {
    UUID tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
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
    CurrentPractitioner principal =
        new CurrentPractitioner(id, tenant, email, "Dr Test", "psychologist", "none");
    auth =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions postJson(String url, String body, String language) throws Exception {
    return mvc.perform(
        post(url)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .header("Accept-Language", language)
            .with(auth));
  }

  @Test
  void arabicGetsTheFrenchErrors() throws Exception {
    for (String body :
        new String[] {
          "{\"labelFr\":\"Lassitude\"}",
          "{\"labelFr\":\"Lassitude\",\"family\":\"nope\"}",
          "{\"labelFr\":\"Anxiete\",\"family\":\"fear\"}",
          "{nope"
        }) {
      assertArabicGetsFrench(
          postJson("/api/me/emotions", body, "fr").andExpect(status().is4xxClientError()),
          postJson("/api/me/emotions", body, ARABIC));
    }
  }

  @Test
  void arabicReadsAnswerLikeFrenchOnes() throws Exception {
    for (String url : new String[] {"/api/vocabulary/emotions", "/api/vocabulary/entry-types"}) {
      assertArabicGetsFrench(
          mvc.perform(get(url).header("Accept-Language", "fr").with(auth))
              .andExpect(status().isOk()),
          mvc.perform(get(url).header("Accept-Language", ARABIC).with(auth)));
    }
  }
}
