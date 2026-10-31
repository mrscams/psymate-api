package com.psymate.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.psymate.identity.api.CurrentPractitioner;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** M1-14b : administration de la liste fermée des spécialités et publics de l'annuaire (Q15). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DirectoryTestConfig.class)
class DirectoryVocabularyAdminIT {

  private static final String BASE = "/api/admin/vocabulary/directory";

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  private final JsonMapper json = JsonMapper.builder().build();

  private UUID tenant;

  @BeforeEach
  void setUp() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  // ---- fixtures

  private UUID account(String role, boolean totp) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, email_confirmed_at,"
            + " password_hash, first_name, last_name, display_name, profession,"
            + " verification_status, role, totp_secret_enc, totp_enabled_at)"
            + " values (?, ?, ?, ?, now(), 'x', 'Test', 'Compte', 'Dr Vocabulaire', 'psychologist',"
            + " 'approved', ?, ?, ?)",
        id,
        tenant,
        email,
        email,
        role,
        totp ? new byte[] {1} : null,
        totp ? Timestamp.from(Instant.now()) : null);
    return id;
  }

  private RequestPostProcessor as(UUID id, String authority) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            id, tenant, id + "@example.test", "Dr Test", "psychologist", "approved");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of(new SimpleGrantedAuthority(authority))));
  }

  private RequestPostProcessor admin() {
    return as(account("admin", true), "ROLE_ADMIN");
  }

  private void publishedProfile(String slug, String specialtyCode, String audienceCode) {
    UUID id = account("practitioner", false);
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, published, display_name,"
            + " specialties, audiences, city) values (?, ?, true, 'Dr Vocabulaire',"
            + " string_to_array(?, ','), string_to_array(?, ','), 'Lyon')",
        id,
        slug,
        specialtyCode == null ? "" : specialtyCode,
        audienceCode == null ? "" : audienceCode);
  }

  private static String code() {
    return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private MvcResult create(RequestPostProcessor who, Map<String, Object> payload) throws Exception {
    return mvc.perform(
            post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload))
                .with(who))
        .andReturn();
  }

  private MvcResult patchEntry(
      RequestPostProcessor who, String kind, String code, Map<String, Object> payload)
      throws Exception {
    return mvc.perform(
            patch(BASE + "/" + kind + "/" + code)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload))
                .with(who))
        .andReturn();
  }

  private static Map<String, Object> entry(String kind, String code, String labelFr) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("kind", kind);
    m.put("code", code);
    m.put("labelFr", labelFr);
    return m;
  }

  private JsonNode publicList(String key) throws Exception {
    MvcResult r =
        mvc.perform(
                get("/api/vocabulary/directory")
                    .with(as(account("practitioner", false), "ROLE_PRACTITIONER")))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return body(r).path(key);
  }

  private boolean publicHas(String key, String code) throws Exception {
    for (JsonNode n : publicList(key)) {
      if (code.equals(n.path("code").asString())) {
        return true;
      }
    }
    return false;
  }

  // ---- accès

  @Test
  void nonAdminAndAdminWithoutTwoFactorAreRefusedOnEveryRoute() throws Exception {
    RequestPostProcessor practitioner = as(account("practitioner", false), "ROLE_PRACTITIONER");
    RequestPostProcessor noTotp = as(account("admin", false), "ROLE_ADMIN");
    Map<String, RequestPostProcessor> callers =
        Map.of("ADMIN_ROLE_REQUIRED", practitioner, "TWO_FACTOR_REQUIRED", noTotp);
    for (var caller : callers.entrySet()) {
      List<MvcResult> results =
          List.of(
              mvc.perform(get(BASE).with(caller.getValue())).andReturn(),
              create(caller.getValue(), entry("specialty", code(), "Interdit")),
              patchEntry(caller.getValue(), "specialty", "anxiety", Map.of("active", false)));
      for (MvcResult r : results) {
        assertThat(r.getResponse().getStatus()).as(caller.getKey()).isEqualTo(403);
        assertThat(body(r).path("code").asString()).isEqualTo(caller.getKey());
      }
    }
    assertThat(mvc.perform(get(BASE)).andReturn().getResponse().getStatus()).isEqualTo(401);
    assertThat(
            jdbc.queryForObject(
                "select active from directory_vocabulary where kind = 'specialty'"
                    + " and code = 'anxiety'",
                Boolean.class))
        .isTrue();
  }

  @Test
  void thereIsNoDeleteRoute() throws Exception {
    MvcResult r = mvc.perform(delete(BASE + "/specialty/anxiety").with(admin())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(405);
  }

  // ---- liste

  @Test
  void listIncludesInactiveEntriesAndFiltersByKind() throws Exception {
    RequestPostProcessor admin = admin();
    String code = code();
    assertThat(status(create(admin, entry("specialty", code, "Inactive un jour")))).isEqualTo(201);
    assertThat(status(patchEntry(admin, "specialty", code, Map.of("active", false))))
        .isEqualTo(200);

    MvcResult all = mvc.perform(get(BASE).with(admin)).andReturn();
    assertThat(status(all)).isEqualTo(200);
    JsonNode items = body(all).path("items");
    boolean foundInactive = false;
    boolean seeded = false;
    for (JsonNode n : items) {
      if (code.equals(n.path("code").asString())) {
        foundInactive = !n.path("active").asBoolean();
      }
      if ("anxiety".equals(n.path("code").asString())) {
        seeded = n.path("active").asBoolean();
        assertThat(n.path("labelFr").asString()).isEqualTo("Anxiété");
        assertThat(n.has("usedByPublishedProfiles")).isTrue();
      }
    }
    assertThat(foundInactive).isTrue();
    assertThat(seeded).isTrue();

    JsonNode audiences =
        body(mvc.perform(get(BASE + "?kind=audience").with(admin)).andReturn()).path("items");
    assertThat(audiences).isNotEmpty();
    for (JsonNode n : audiences) {
      assertThat(n.path("kind").asString()).isEqualTo("audience");
    }
    MvcResult bad = mvc.perform(get(BASE + "?kind=unknown").with(admin)).andReturn();
    assertThat(status(bad)).isEqualTo(422);
    assertThat(body(bad).path("errors").get(0).path("field").asString()).isEqualTo("kind");
  }

  // ---- création

  @Test
  void createReturns201WithDefaultsThenConflictsOnDuplicate() throws Exception {
    RequestPostProcessor admin = admin();
    String code = code();
    Map<String, Object> payload = entry("audience", code, "Seniors");
    payload.put("labelEn", "Seniors");
    MvcResult created = create(admin, payload);
    assertThat(status(created)).isEqualTo(201);
    JsonNode item = body(created);
    assertThat(item.path("kind").asString()).isEqualTo("audience");
    assertThat(item.path("code").asString()).isEqualTo(code);
    assertThat(item.path("labelFr").asString()).isEqualTo("Seniors");
    assertThat(item.path("labelEn").asString()).isEqualTo("Seniors");
    assertThat(item.path("active").asBoolean()).isTrue();
    assertThat(item.path("usedByPublishedProfiles").asInt()).isZero();
    int max =
        jdbc.queryForObject(
            "select max(sort_order) from directory_vocabulary where kind = 'audience'"
                + " and code <> ?",
            Integer.class,
            code);
    assertThat(item.path("sortOrder").asInt()).isEqualTo(max + 1);
    assertThat(publicHas("audiences", code)).isTrue();

    MvcResult duplicate = create(admin, entry("audience", code, "Autre libellé"));
    assertThat(status(duplicate)).isEqualTo(409);
    assertThat(body(duplicate).path("code").asString()).isEqualTo("VOCABULARY_EXISTS");
    assertThat(duplicate.getResponse().getContentType()).contains("application/problem+json");
  }

  @Test
  void createRejectsInvalidFieldsWithPerFieldErrors() throws Exception {
    RequestPostProcessor admin = admin();
    MvcResult r = create(admin, entry("hobby", "Code Invalide", " "));
    assertThat(status(r)).isEqualTo(422);
    assertThat(body(r).path("code").asString()).isEqualTo("VALIDATION_FAILED");
    List<String> fields = new java.util.ArrayList<>();
    for (JsonNode e : body(r).path("errors")) {
      fields.add(e.path("field").asString());
      assertThat(e.path("code").asString()).isNotBlank();
      assertThat(e.path("message").asString()).isNotBlank();
    }
    assertThat(fields).containsExactlyInAnyOrder("kind", "code", "labelFr");

    for (String bad : List.of("A1", "1abc", "a", "ab-cd", "x".repeat(41), "a b")) {
      assertThat(status(create(admin, entry("specialty", bad, "Libellé")))).as(bad).isEqualTo(422);
    }
    Map<String, Object> tooLong = entry("specialty", code(), "x".repeat(121));
    tooLong.put("labelEn", "y".repeat(121));
    tooLong.put("sortOrder", 40000);
    MvcResult long422 = create(admin, tooLong);
    assertThat(status(long422)).isEqualTo(422);
    List<String> longFields = new java.util.ArrayList<>();
    for (JsonNode e : body(long422).path("errors")) {
      longFields.add(e.path("field").asString());
    }
    assertThat(longFields).containsExactlyInAnyOrder("labelFr", "labelEn", "sortOrder");
  }

  // ---- modification

  @Test
  void patchUpdatesLabelsAndOrderAndMissingEntryIs404() throws Exception {
    RequestPostProcessor admin = admin();
    String code = code();
    create(admin, entry("specialty", code, "Ancien libellé"));
    Instant before =
        jdbc.queryForObject(
                "select updated_at from directory_vocabulary where kind = 'specialty' and code = ?",
                Timestamp.class,
                code)
            .toInstant();

    Map<String, Object> change = new LinkedHashMap<>();
    change.put("labelFr", "Nouveau libellé");
    change.put("labelEn", "New label");
    change.put("sortOrder", 99);
    MvcResult r = patchEntry(admin, "specialty", code, change);
    assertThat(status(r)).isEqualTo(200);
    assertThat(body(r).path("labelFr").asString()).isEqualTo("Nouveau libellé");
    assertThat(body(r).path("labelEn").asString()).isEqualTo("New label");
    assertThat(body(r).path("sortOrder").asInt()).isEqualTo(99);
    assertThat(body(r).path("active").asBoolean()).isTrue();
    Instant after =
        jdbc.queryForObject(
                "select updated_at from directory_vocabulary where kind = 'specialty' and code = ?",
                Timestamp.class,
                code)
            .toInstant();
    assertThat(after).isAfterOrEqualTo(before);

    MvcResult empty = patchEntry(admin, "specialty", code, Map.of("labelFr", " "));
    assertThat(status(empty)).isEqualTo(422);
    assertThat(body(empty).path("errors").get(0).path("field").asString()).isEqualTo("labelFr");

    MvcResult missing = patchEntry(admin, "specialty", "does_not_exist", Map.of("active", false));
    assertThat(status(missing)).isEqualTo(404);
    assertThat(body(missing).path("code").asString()).isEqualTo("VOCABULARY_NOT_FOUND");
    assertThat(status(patchEntry(admin, "hobby", code, Map.of("active", false)))).isEqualTo(404);
  }

  @Test
  void deactivatingACodeUsedByAPublishedProfileIsRefused() throws Exception {
    RequestPostProcessor admin = admin();
    String specialty = code();
    String audience = code();
    create(admin, entry("specialty", specialty, "Spécialité utilisée"));
    create(admin, entry("audience", audience, "Public utilisé"));
    publishedProfile("vocab-" + specialty, specialty, audience);
    // Fiche non publiée : n'empêche rien.
    UUID draft = account("practitioner", false);
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, published, display_name,"
            + " specialties) values (?, ?, false, 'Brouillon', ARRAY[?])",
        draft,
        "draft-" + specialty,
        specialty);

    MvcResult used = patchEntry(admin, "specialty", specialty, Map.of("active", false));
    assertThat(status(used)).isEqualTo(409);
    assertThat(body(used).path("code").asString()).isEqualTo("VOCABULARY_IN_USE");
    assertThat(body(used).path("usedByPublishedProfiles").asInt()).isEqualTo(1);
    assertThat(used.getResponse().getContentType()).contains("application/problem+json");
    MvcResult usedAudience = patchEntry(admin, "audience", audience, Map.of("active", false));
    assertThat(status(usedAudience)).isEqualTo(409);
    assertThat(body(usedAudience).path("usedByPublishedProfiles").asInt()).isEqualTo(1);
    assertThat(publicHas("specialties", specialty)).isTrue();

    JsonNode listed =
        body(mvc.perform(get(BASE + "?kind=specialty").with(admin)).andReturn()).path("items");
    for (JsonNode n : listed) {
      if (specialty.equals(n.path("code").asString())) {
        assertThat(n.path("usedByPublishedProfiles").asInt()).isEqualTo(1);
        assertThat(n.path("active").asBoolean()).isTrue();
      }
    }
    // Les libellés restent modifiables même quand le code est utilisé.
    assertThat(status(patchEntry(admin, "specialty", specialty, Map.of("labelFr", "Renommée"))))
        .isEqualTo(200);

    // Après dépublication de la fiche, la désactivation passe.
    jdbc.update(
        "update practitioner_profile set published = false where slug = ?", "vocab-" + specialty);
    assertThat(status(patchEntry(admin, "specialty", specialty, Map.of("active", false))))
        .isEqualTo(200);
  }

  @Test
  void deactivatingAnUnusedCodeRemovesItFromThePublicVocabulary() throws Exception {
    RequestPostProcessor admin = admin();
    String code = code();
    create(admin, entry("specialty", code, "Éphémère"));
    assertThat(publicHas("specialties", code)).isTrue();

    MvcResult off = patchEntry(admin, "specialty", code, Map.of("active", false));
    assertThat(status(off)).isEqualTo(200);
    assertThat(body(off).path("active").asBoolean()).isFalse();
    assertThat(publicHas("specialties", code)).isFalse();

    MvcResult on = patchEntry(admin, "specialty", code, Map.of("active", true));
    assertThat(status(on)).isEqualTo(200);
    assertThat(publicHas("specialties", code)).isTrue();
  }

  @Test
  void errorMessagesFollowTheRequestedLanguage() throws Exception {
    RequestPostProcessor admin = admin();
    String fr = messageIn("fr", admin);
    String en = messageIn("en", admin);
    assertThat(fr).contains("commencer");
    assertThat(en).contains("start with");
  }

  /**
   * Un brouillon enregistré avec un code actif, désactivé ensuite par l'administration, ne peut pas
   * être publié : la règle « un profil publié n'utilise que des codes actifs » tient aussi à la
   * publication, pas seulement au PATCH du profil.
   */
  @Test
  void aDraftUsingACodeDeactivatedSinceCannotBePublished() throws Exception {
    RequestPostProcessor admin = admin();
    String specialty = code();
    create(admin, entry("specialty", specialty, "Spécialité éphémère"));
    UUID owner = account("practitioner", false);
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, published, display_name,"
            + " specialties, city, contact_phone, show_phone) values (?, ?, false, 'Dr Brouillon',"
            + " ARRAY[?], 'Lyon', '06 12 34 56 78', true)",
        owner,
        "draft-" + specialty,
        specialty);
    assertThat(status(patchEntry(admin, "specialty", specialty, Map.of("active", false))))
        .isEqualTo(200);

    MvcResult refused =
        mvc.perform(post("/api/me/profile/publish").with(as(owner, "ROLE_PRACTITIONER")))
            .andReturn();
    assertThat(status(refused)).isEqualTo(409);
    assertThat(body(refused).path("code").asString()).isEqualTo("PROFILE_INCOMPLETE");
    List<String> missing = new java.util.ArrayList<>();
    body(refused).path("missing").forEach(n -> missing.add(n.asString()));
    assertThat(missing).containsExactly("specialties");
    assertThat(
            jdbc.queryForObject(
                "select published from practitioner_profile where practitioner_id = ?",
                Boolean.class,
                owner))
        .isFalse();

    // Réactivé, le code redevient publiable.
    assertThat(status(patchEntry(admin, "specialty", specialty, Map.of("active", true))))
        .isEqualTo(200);
    assertThat(
            status(
                mvc.perform(post("/api/me/profile/publish").with(as(owner, "ROLE_PRACTITIONER")))
                    .andReturn()))
        .isEqualTo(204);
  }

  private String messageIn(String language, RequestPostProcessor admin) throws Exception {
    MvcResult r =
        mvc.perform(
                post(BASE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept-Language", language)
                    .content(json.writeValueAsString(entry("specialty", "BAD", "x")))
                    .with(admin))
            .andReturn();
    return body(r).path("errors").get(0).path("message").asString();
  }

  private int status(MvcResult r) {
    return r.getResponse().getStatus();
  }
}
