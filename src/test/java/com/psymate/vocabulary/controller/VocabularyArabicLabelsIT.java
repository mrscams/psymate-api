package com.psymate.vocabulary.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M16-03 (G Q98) : émotions, types d'entrée et types de lien exposent labelAr (et inverseLabelAr).
 * Une ligne sans libellé arabe le rend nul, et labelFr reste là pour le repli ; les libellés
 * français et anglais ne changent pas, quelle que soit la langue demandée.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class VocabularyArabicLabelsIT {

  private static final JsonMapper JSON = JsonMapper.builder().build();

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

  private String read(String url, String language) throws Exception {
    return mvc.perform(get(url).header("Accept-Language", language).with(auth))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private static JsonNode byCode(String body, String code) {
    for (JsonNode item : JSON.readTree(body)) {
      if (code.equals(item.path("code").asString())) {
        return item;
      }
    }
    throw new AssertionError("code absent : " + code);
  }

  /** Remet une colonne d'une ligne globale à sa valeur d'origine, même après un échec. */
  private void withNull(String table, String column, String code, Runnable check) {
    String before =
        jdbc.queryForObject(
            "select " + column + " from " + table + " where code = ?", String.class, code);
    jdbc.update("update " + table + " set " + column + " = null where code = ?", code);
    try {
      check.run();
    } finally {
      jdbc.update("update " + table + " set " + column + " = ? where code = ?", before, code);
    }
  }

  private String readUnchecked(String url, String language) {
    try {
      return read(url, language);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void emotionsCarryTheirArabicLabel() throws Exception {
    JsonNode anxiety = byCode(read("/api/vocabulary/emotions", ARABIC), "anxiety");
    assertThat(anxiety.path("labelAr").asString()).isEqualTo("قلق");
    assertThat(anxiety.path("labelFr").asString()).isEqualTo("Anxiété");
    assertThat(anxiety.path("labelEn").asString()).isEqualTo("Anxiety");
  }

  @Test
  void anEmotionWithoutArabicLabelGivesNullAndKeepsTheFrench() throws Exception {
    // Émotion personnalisée : un seul libellé, celui du praticien.
    String created =
        mvc.perform(
                post("/api/me/emotions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"labelFr\":\"Lassitude\",\"family\":\"sadness\"}")
                    .header("Accept-Language", ARABIC)
                    .with(auth))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    JsonNode custom = JSON.readTree(created);
    assertThat(custom.path("labelAr").isNull()).isTrue();
    JsonNode listed =
        byCode(read("/api/vocabulary/emotions", ARABIC), custom.path("code").asString());
    assertThat(listed.path("labelAr").isNull()).isTrue();
    assertThat(listed.path("labelFr").asString()).isEqualTo("Lassitude");
    assertThat(
            jdbc.queryForObject(
                "select label_ar from emotion where code = ?",
                String.class,
                custom.path("code").asString()))
        .isNull();

    // Émotion globale dont le libellé arabe manque.
    withNull(
        "emotion",
        "label_ar",
        "calm",
        () -> {
          JsonNode calm = byCode(readUnchecked("/api/vocabulary/emotions", ARABIC), "calm");
          assertThat(calm.path("labelAr").isNull()).isTrue();
          assertThat(calm.path("labelFr").asString()).isEqualTo("Calme");
        });
  }

  @Test
  void entryTypesCarryTheirArabicLabelOrNull() throws Exception {
    JsonNode feeling = byCode(read("/api/vocabulary/entry-types", ARABIC), "feeling");
    assertThat(feeling.path("labelAr").asString()).isEqualTo("شعور");
    assertThat(feeling.path("labelFr").asString()).isEqualTo("Ressenti");
    assertThat(feeling.path("labelEn").asString()).isEqualTo("Feeling");
    withNull(
        "entry_type",
        "label_ar",
        "note",
        () -> {
          JsonNode note = byCode(readUnchecked("/api/vocabulary/entry-types", ARABIC), "note");
          assertThat(note.path("labelAr").isNull()).isTrue();
          assertThat(note.path("labelFr").asString()).isEqualTo("Note");
        });
  }

  @Test
  void linkTypesCarryTheirArabicLabelsAndInverses() throws Exception {
    String body = read("/api/vocabulary/link-types", ARABIC);
    JsonNode triggers = byCode(body, "triggers");
    assertThat(triggers.path("labelAr").asString()).isEqualTo("يُثير");
    assertThat(triggers.path("inverseLabelAr").asString()).isEqualTo("يُثيره");
    assertThat(triggers.path("labelFr").asString()).isEqualTo("déclenche");
    assertThat(triggers.path("inverseLabelFr").asString()).isEqualTo("déclenché par");
    assertThat(triggers.path("labelEn").asString()).isEqualTo("triggers");
    assertThat(triggers.path("inverseLabelEn").asString()).isEqualTo("triggered by");
    JsonNode coOccurs = byCode(body, "co_occurs");
    assertThat(coOccurs.path("labelAr").asString()).isEqualTo("يتزامن مع");
    assertThat(coOccurs.has("inverseLabelAr")).isTrue();
    assertThat(coOccurs.path("inverseLabelAr").isNull()).isTrue();
  }

  /** Les catalogues ne dépendent pas de la langue : fr, en et ar rendent le même corps. */
  @Test
  void frenchEnglishAndArabicReadsAreTheSame() throws Exception {
    for (String url :
        List.of(
            "/api/vocabulary/emotions",
            "/api/vocabulary/entry-types",
            "/api/vocabulary/link-types")) {
      String french = read(url, "fr");
      assertThat(read(url, "en")).as(url).isEqualTo(french);
      assertThat(read(url, ARABIC)).as(url).isEqualTo(french);
    }
  }
}
