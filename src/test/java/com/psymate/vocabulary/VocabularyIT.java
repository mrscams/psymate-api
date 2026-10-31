package com.psymate.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.vocabulary.VocabularyTestConfig.SwitchablePractitioner;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VocabularyTestConfig.class)
class VocabularyIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private SwitchablePractitioner current;

  private UUID tenant;
  private UUID alice;
  private UUID bob;

  @BeforeEach
  void practitioners() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    alice = insertPractitioner();
    bob = insertPractitioner();
    current.use(tenant, alice);
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

  private ResultActions postJson(String url, String body) throws Exception {
    return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions patchJson(String url, String body) throws Exception {
    return mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private String createEmotion(String label, String family) throws Exception {
    String json =
        postJson(
                "/api/me/emotions", "{\"labelFr\":\"" + label + "\",\"family\":\"" + family + "\"}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.replaceAll(".*\"code\":\"([^\"]+)\".*", "$1");
  }

  private String createTag(String label) throws Exception {
    String json =
        postJson("/api/tags", "{\"label\":\"" + label + "\"}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
  }

  // ---- seed

  @Test
  void seedHasSixEntryTypes29EmotionsAndEightLinkTypes() {
    assertThat(jdbc.queryForObject("select count(*) from entry_type", Integer.class)).isEqualTo(6);
    assertThat(jdbc.queryForObject("select count(*) from emotion", Integer.class))
        .isGreaterThanOrEqualTo(29);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from emotion where scope = 'global' and active", Integer.class))
        .isEqualTo(29);
    assertThat(jdbc.queryForObject("select count(*) from link_type", Integer.class)).isEqualTo(8);
  }

  @Test
  void everyAllowedTypeCodeExistsInEntryType() {
    Integer orphans =
        jdbc.queryForObject(
            """
            select count(*) from (
              select unnest(allowed_source_types) as c from link_type
              union all
              select unnest(allowed_target_types) from link_type
            ) t where c not in (select code from entry_type)
            """,
            Integer.class);
    assertThat(orphans).isZero();
  }

  @Test
  void javaNormalizationMatchesSqlFunction() {
    List<String> labels =
        List.of(
            "Anxiété",
            "ÉCOLE",
            "#Mélancolie",
            " Dégoût ",
            "Culpabilité",
            "ça va",
            "naïveté",
            "Être à l'écoute",
            "Ôtez-moi ça",
            "#  Très   fatigué ",
            "Œuvre",
            "Zoë");
    for (String label : labels) {
      String sql =
          jdbc.queryForObject(
              "select lower(f_unaccent(trim(replace(?, '#', ''))))", String.class, label);
      assertThat(TextNormalizer.normalize(label)).as(label).isEqualTo(sql);
    }
  }

  // ---- lectures

  @Test
  void emotionsReturn29GlobalsPlusOwnCustomOnly() throws Exception {
    mvc.perform(get("/api/vocabulary/emotions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(29));

    String lassitude = createEmotion("Lassitude", "sadness");
    current.use(tenant, bob);
    createEmotion("Euphorie", "joy");

    mvc.perform(get("/api/vocabulary/emotions"))
        .andExpect(jsonPath("$.length()").value(30))
        .andExpect(jsonPath("$[?(@.labelFr=='Euphorie')]").exists())
        .andExpect(jsonPath("$[?(@.code=='" + lassitude + "')]").doesNotExist());

    current.use(tenant, alice);
    mvc.perform(get("/api/vocabulary/emotions"))
        .andExpect(jsonPath("$.length()").value(30))
        .andExpect(jsonPath("$[?(@.code=='" + lassitude + "')]").exists())
        .andExpect(jsonPath("$[?(@.labelFr=='Euphorie')]").doesNotExist());
  }

  @Test
  void linkTypesExposeMatrix() throws Exception {
    mvc.perform(get("/api/vocabulary/link-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(8))
        .andExpect(jsonPath("$[0].code").value("triggers"))
        .andExpect(jsonPath("$[0].allowedSourceTypes.length()").value(3))
        .andExpect(jsonPath("$[0].allowedTargetTypes[0]").value("feeling"));
  }

  @Test
  void entryTypesAreListed() throws Exception {
    mvc.perform(get("/api/vocabulary/entry-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[0].code").value("note"));
  }

  // ---- émotions personnalisées

  @Test
  void createsCustomEmotionWithGeneratedCode() throws Exception {
    postJson("/api/me/emotions", "{\"labelFr\":\"Lassitude\",\"family\":\"sadness\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("c_[0-9a-f]{8}")))
        .andExpect(jsonPath("$.scope").value("custom"))
        .andExpect(jsonPath("$.family").value("sadness"));
    Map<String, Object> row =
        jdbc.queryForMap(
            "select practitioner_id, label_normalized from emotion where label_fr = 'Lassitude'"
                + " and practitioner_id = ?",
            alice);
    assertThat(row.get("label_normalized")).isEqualTo("lassitude");
  }

  @Test
  void customEmotionWithoutFamilyIs422() throws Exception {
    postJson("/api/me/emotions", "{\"labelFr\":\"Lassitude\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("family"));
    postJson("/api/me/emotions", "{\"labelFr\":\"Lassitude\",\"family\":\"nope\"}")
        .andExpect(status().isUnprocessableEntity());
    postJson("/api/me/emotions", "{\"family\":\"joy\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("labelFr"));
  }

  @Test
  void duplicateCustomLabelForSamePractitionerIs409() throws Exception {
    createEmotion("Lassitude", "sadness");
    postJson("/api/me/emotions", "{\"labelFr\":\"lassitude\",\"family\":\"sadness\"}")
        .andExpect(status().isConflict());
    postJson("/api/me/emotions", "{\"labelFr\":\"LASSITUDE\",\"family\":\"fear\"}")
        .andExpect(status().isConflict());
    current.use(tenant, bob);
    createEmotion("Lassitude", "sadness");
  }

  @Test
  void customLabelEqualToGlobalEmotionIs409() throws Exception {
    postJson("/api/me/emotions", "{\"labelFr\":\"Anxiete\",\"family\":\"fear\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMOTION_LABEL_EXISTS"));
  }

  @Test
  void renamesCustomEmotion() throws Exception {
    String code = createEmotion("Lassitude", "sadness");
    patchJson("/api/me/emotions/" + code, "{\"labelFr\":\"Fatigue morale\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.labelFr").value("Fatigue morale"));
    assertThat(
            jdbc.queryForObject(
                "select label_normalized from emotion where code = ?", String.class, code))
        .isEqualTo("fatigue morale");
    patchJson("/api/me/emotions/" + code, "{\"labelFr\":\"Joie\"}")
        .andExpect(status().isConflict());
    patchJson("/api/me/emotions/anxiety", "{\"labelFr\":\"Autre\"}")
        .andExpect(status().isNotFound());
    current.use(tenant, bob);
    patchJson("/api/me/emotions/" + code, "{\"labelFr\":\"Volé\"}")
        .andExpect(status().isNotFound());
  }

  // ---- fusion d'émotions

  @Test
  void mergeCreatesMergeRowAndDeactivatesSource() throws Exception {
    String code = createEmotion("Lassitude", "sadness");
    String json =
        postJson("/api/me/emotions/" + code + "/merge", "{\"targetCode\":\"sadness\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceCode").value(code))
            .andExpect(jsonPath("$.targetCode").value("sadness"))
            .andExpect(jsonPath("$.affectedCount").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID mergeId = UUID.fromString(json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

    Map<String, Object> row =
        jdbc.queryForMap(
            "select practitioner_id, undo_until - merged_at as delay,"
                + " undo_until > now() + interval '29 days' as ok from emotion_merge where id = ?",
            mergeId);
    assertThat(row.get("practitioner_id")).isEqualTo(alice);
    assertThat(row.get("ok")).isEqualTo(true);
    assertThat(
            jdbc.queryForObject("select active from emotion where code = ?", Boolean.class, code))
        .isFalse();
    mvc.perform(get("/api/vocabulary/emotions"))
        .andExpect(jsonPath("$[?(@.code=='" + code + "')]").doesNotExist());
  }

  @Test
  void mergeRejectsBadTargets() throws Exception {
    String code = createEmotion("Lassitude", "sadness");
    postJson("/api/me/emotions/" + code + "/merge", "{\"targetCode\":\"" + code + "\"}")
        .andExpect(status().isUnprocessableEntity());
    postJson("/api/me/emotions/" + code + "/merge", "{\"targetCode\":\"inexistante\"}")
        .andExpect(status().isNotFound());
    current.use(tenant, bob);
    String bobs = createEmotion("Euphorie", "joy");
    current.use(tenant, alice);
    postJson("/api/me/emotions/" + code + "/merge", "{\"targetCode\":\"" + bobs + "\"}")
        .andExpect(status().isNotFound());
  }

  @Test
  void undoRestoresSourceThenRefusesSecondUndo() throws Exception {
    String code = createEmotion("Lassitude", "sadness");
    String json =
        postJson("/api/me/emotions/" + code + "/merge", "{\"targetCode\":\"sadness\"}")
            .andReturn()
            .getResponse()
            .getContentAsString();
    String mergeId = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

    postJson("/api/me/emotions/merges/" + mergeId + "/undo", "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.undoneAt").isNotEmpty());
    assertThat(
            jdbc.queryForObject("select active from emotion where code = ?", Boolean.class, code))
        .isTrue();
    postJson("/api/me/emotions/merges/" + mergeId + "/undo", "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MERGE_ALREADY_UNDONE"));
  }

  @Test
  void undoAfterDeadlineIs409() throws Exception {
    String code = createEmotion("Lassitude", "sadness");
    String json =
        postJson("/api/me/emotions/" + code + "/merge", "{\"targetCode\":\"sadness\"}")
            .andReturn()
            .getResponse()
            .getContentAsString();
    String mergeId = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    jdbc.update(
        "update emotion_merge set undo_until = now() - interval '1 second' where id = ?::uuid",
        mergeId);

    postJson("/api/me/emotions/merges/" + mergeId + "/undo", "{}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MERGE_UNDO_EXPIRED"));
    assertThat(
            jdbc.queryForObject("select active from emotion where code = ?", Boolean.class, code))
        .isFalse();
  }

  @Test
  void undoOfAnotherPractitionersMergeIs404() throws Exception {
    String code = createEmotion("Lassitude", "sadness");
    String json =
        postJson("/api/me/emotions/" + code + "/merge", "{\"targetCode\":\"sadness\"}")
            .andReturn()
            .getResponse()
            .getContentAsString();
    String mergeId = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    current.use(tenant, bob);
    postJson("/api/me/emotions/merges/" + mergeId + "/undo", "{}").andExpect(status().isNotFound());
  }

  /**
   * Dépend de entry_feeling, créée par M1-03 (V05). À activer alors : insérer deux ressentis sur la
   * custom, fusionner, vérifier emotion_code = cible, original_emotion_code = source, puis undo.
   */
  @Test
  @Disabled("M1-03")
  void mergeRewritesEntryFeelingAndUndoRestoresIt() throws Exception {
    String code = createEmotion("Lassitude", "sadness");
    // M1-03 : créer 2 entry (type feeling) + entry_feeling(emotion_code = code), puis :
    String json =
        postJson("/api/me/emotions/" + code + "/merge", "{\"targetCode\":\"sadness\"}")
            .andExpect(jsonPath("$.affectedCount").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from entry_feeling where emotion_code = 'sadness'"
                    + " and original_emotion_code = ?",
                Integer.class,
                code))
        .isEqualTo(2);
    String mergeId = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    postJson("/api/me/emotions/merges/" + mergeId + "/undo", "{}").andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from entry_feeling where emotion_code = ?", Integer.class, code))
        .isEqualTo(2);
  }

  // ---- tags

  @Test
  void tagEqualToEmotionIs422WithSuggestion() throws Exception {
    for (String label : List.of("anxiété", "Anxiete", "#anxiété")) {
      postJson("/api/tags", "{\"label\":\"" + label + "\"}")
          .andExpect(status().isUnprocessableEntity())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("TAG_IS_EMOTION"))
          .andExpect(jsonPath("$.suggestedEmotion.code").value("anxiety"))
          .andExpect(jsonPath("$.errors[0].field").value("label"));
    }
  }

  @Test
  void tagEqualToOwnCustomEmotionIs422ButNotToAnothersCustom() throws Exception {
    createEmotion("Lassitude", "sadness");
    postJson("/api/tags", "{\"label\":\"lassitude\"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("TAG_IS_EMOTION"));
    current.use(tenant, bob);
    postJson("/api/tags", "{\"label\":\"lassitude\"}").andExpect(status().isCreated());
  }

  @Test
  void createsListsAndRenamesTags() throws Exception {
    String id = createTag("#Travail");
    assertThat(
            jdbc.queryForObject(
                "select normalized_label from tag where id = ?::uuid", String.class, id))
        .isEqualTo("travail");
    postJson("/api/tags", "{\"label\":\"travail\"}").andExpect(status().isConflict());
    postJson("/api/tags", "{\"label\":\"  \"}")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("label"));

    mvc.perform(get("/api/tags"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].label").value("Travail"));

    patchJson("/api/tags/" + id, "{\"label\":\"Boulot\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label").value("Boulot"))
        .andExpect(jsonPath("$.normalizedLabel").value("boulot"));
    patchJson("/api/tags/" + id, "{\"label\":\"Joie\"}")
        .andExpect(status().isUnprocessableEntity());

    current.use(tenant, bob);
    mvc.perform(get("/api/tags")).andExpect(jsonPath("$.length()").value(0));
    patchJson("/api/tags/" + id, "{\"label\":\"Volé\"}").andExpect(status().isNotFound());
  }

  @Test
  void mergeTagsArchivesSourceAndAddsUsage() throws Exception {
    String source = createTag("Boulot");
    String target = createTag("Travail");
    jdbc.update("update tag set usage_count = 3 where id = ?::uuid", source);
    jdbc.update("update tag set usage_count = 4 where id = ?::uuid", target);

    postJson("/api/tags/" + source + "/merge", "{\"targetId\":\"" + target + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(target))
        .andExpect(jsonPath("$.usageCount").value(7));

    Map<String, Object> row =
        jdbc.queryForMap(
            "select archived_at is not null as archived, merged_into::text as merged_into"
                + " from tag where id = ?::uuid",
            source);
    assertThat(row.get("archived")).isEqualTo(true);
    assertThat(row.get("merged_into")).isEqualTo(target);
    mvc.perform(get("/api/tags")).andExpect(jsonPath("$.length()").value(1));
    postJson("/api/tags/" + source + "/merge", "{\"targetId\":\"" + target + "\"}")
        .andExpect(status().isNotFound());
    postJson("/api/tags/" + target + "/merge", "{\"targetId\":\"" + target + "\"}")
        .andExpect(status().isUnprocessableEntity());
    // le libellé de la source archivée est de nouveau libre
    createTag("Boulot");
  }

  /** Dépend de entry_tag (M1-03) : les entrées étiquetées par la source passent à la cible. */
  @Test
  @Disabled("M1-03")
  void mergeTagsRelabelsEntries() throws Exception {
    String source = createTag("Boulot");
    String target = createTag("Travail");
    // M1-03 : créer 2 entry, les lier à source dans entry_tag (dont une déjà liée aux deux)
    postJson("/api/tags/" + source + "/merge", "{\"targetId\":\"" + target + "\"}")
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from entry_tag where tag_id = ?::uuid", Integer.class, source))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from entry_tag where tag_id = ?::uuid", Integer.class, target))
        .isEqualTo(2);
  }
}
