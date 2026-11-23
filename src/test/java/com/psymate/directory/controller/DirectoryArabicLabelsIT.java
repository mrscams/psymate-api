package com.psymate.directory.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import com.psymate.testsupport.TestAdmins;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M16-03 (G Q98) : vocabulaire de l'annuaire en arabe. Spécialités et publics exposent labelAr (nul
 * pour une entrée qui n'en a pas, labelFr restant là pour le repli), les modalités le libellé du
 * glossaire, les langues leur nom donné par le JDK en arabe ; la facette des spécialités aussi.
 * L'administration accepte labelAr avec les règles de labelEn.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class DirectoryArabicLabelsIT {

  private static final String PUBLIC = "/api/public/directory/vocabulary";
  private static final String ADMIN = "/api/admin/vocabulary/directory";
  private static final AtomicInteger IP = new AtomicInteger();
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;

  /** Codes créés par le test en cours : désactivés en fin de test, même après un échec. */
  private final List<String> insertedCodes = new ArrayList<>();

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  @AfterEach
  void cleanUp() {
    for (String c : insertedCodes) {
      jdbc.update("update directory_vocabulary set active = false where code = ?", c);
    }
    TestAdmins.closeInTenant(jdbc, tenant);
  }

  // ---- fixtures

  private static String freshIp() {
    int n = IP.incrementAndGet();
    return "10.17." + (n / 250) + "." + (n % 250 + 1);
  }

  private static MockHttpServletRequestBuilder anon(String url, String language) {
    String ip = freshIp();
    return get(url)
        .header("Accept-Language", language)
        .with(
            r -> {
              r.setRemoteAddr(ip);
              return r;
            });
  }

  private static String code() {
    return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private UUID account(String role, boolean totp, String verificationStatus) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, email_confirmed_at,"
            + " password_hash, first_name, last_name, display_name, profession,"
            + " verification_status, role, totp_secret_enc, totp_enabled_at)"
            + " values (?, ?, ?, ?, now(), 'x', 'Test', 'Compte', 'Dr Annuaire', 'psychologist',"
            + " ?, ?, ?, ?)",
        id,
        tenant,
        email,
        email,
        verificationStatus,
        role,
        totp ? new byte[] {1} : null,
        totp ? Timestamp.from(Instant.now()) : null);
    return id;
  }

  private RequestPostProcessor admin() {
    UUID id = account("admin", true, "approved");
    CurrentPractitioner principal =
        new CurrentPractitioner(
            id, tenant, id + "@example.test", "Dr Test", "psychologist", "approved");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  private String read(MockHttpServletRequestBuilder request) throws Exception {
    MvcResult r = mvc.perform(request).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private static JsonNode byCode(JsonNode list, String code) {
    for (JsonNode item : list) {
      if (code.equals(item.path("code").asString())) {
        return item;
      }
    }
    throw new AssertionError("code absent : " + code);
  }

  private MvcResult send(MockHttpServletRequestBuilder request, Map<String, Object> payload)
      throws Exception {
    return mvc.perform(
            request
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(payload)))
        .andReturn();
  }

  private static JsonNode body(MvcResult r) throws Exception {
    return JSON.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  private static List<String> errorFields(MvcResult r) throws Exception {
    List<String> fields = new ArrayList<>();
    for (JsonNode e : body(r).path("errors")) {
      fields.add(e.path("field").asString() + ":" + e.path("code").asString());
    }
    return fields;
  }

  private String storedLabelAr(String kind, String code) {
    return jdbc.queryForObject(
        "select label_ar from directory_vocabulary where kind = ? and code = ?",
        String.class,
        kind,
        code);
  }

  // ---- lecture

  @Test
  void specialtiesAudiencesAndModalitiesCarryTheGlossaryTerms() throws Exception {
    JsonNode b = JSON.readTree(read(anon(PUBLIC, ARABIC)));
    assertThat(byCode(b.path("specialties"), "anxiety").path("labelAr").asString())
        .isEqualTo("القلق");
    assertThat(byCode(b.path("specialties"), "emdr").path("labelAr").asString()).isEqualTo("EMDR");
    assertThat(byCode(b.path("audiences"), "adults").path("labelAr").asString())
        .isEqualTo("البالغون");
    JsonNode modalities = b.path("modalities");
    assertThat(byCode(modalities, "in_person").path("labelAr").asString()).isEqualTo("حضوريًا");
    assertThat(byCode(modalities, "video").path("labelAr").asString()).isEqualTo("عبر الفيديو");
    assertThat(byCode(modalities, "home").path("labelAr").asString()).isEqualTo("في المنزل");
    // Libellés français et anglais des modalités inchangés.
    assertThat(byCode(modalities, "home").path("labelFr").asString()).isEqualTo("À domicile");
    assertThat(byCode(modalities, "home").path("labelEn").asString()).isEqualTo("At home");
  }

  @Test
  void spokenLanguagesAreNamedInArabicByTheJdk() throws Exception {
    JsonNode languages = JSON.readTree(read(anon(PUBLIC, ARABIC))).path("languages");
    assertThat(languages).hasSize(15);
    Locale arabic = Locale.of("ar");
    List<String> frenchOrder = new ArrayList<>();
    for (JsonNode item : languages) {
      Locale spoken = Locale.of(item.path("code").asString());
      String labelAr = item.path("labelAr").asString();
      assertThat(labelAr).as(item.toString()).isNotBlank();
      assertThat(labelAr).as(item.toString()).isEqualTo(spoken.getDisplayLanguage(arabic));
      assertThat(labelAr).as(item.toString()).isNotEqualTo(item.path("labelFr").asString());
      assertThat(item.path("labelFr").asString())
          .isEqualTo(spoken.getDisplayLanguage(Locale.FRENCH));
      assertThat(item.path("labelEn").asString())
          .isEqualTo(spoken.getDisplayLanguage(Locale.ENGLISH));
      frenchOrder.add(item.path("labelFr").asString());
    }
    // Toujours triées par libellé français.
    assertThat(frenchOrder).isSorted();
  }

  @Test
  void anEntryWithoutArabicLabelGivesNullAndKeepsTheFrench() throws Exception {
    String specialty = code();
    insertedCodes.add(specialty);
    jdbc.update(
        "insert into directory_vocabulary (kind, code, label_fr, label_en, sort_order)"
            + " values ('specialty', ?, 'Spécialité sans arabe', 'No Arabic', 900)",
        specialty);
    JsonNode item =
        byCode(JSON.readTree(read(anon(PUBLIC, ARABIC))).path("specialties"), specialty);
    assertThat(item.has("labelAr")).isTrue();
    assertThat(item.path("labelAr").isNull()).isTrue();
    assertThat(item.path("labelFr").asString()).isEqualTo("Spécialité sans arabe");
  }

  /** Le vocabulaire ne dépend pas de la langue : fr, en et ar rendent le même corps. */
  @Test
  void frenchEnglishAndArabicReadsAreTheSame() throws Exception {
    String french = read(anon(PUBLIC, "fr"));
    assertThat(read(anon(PUBLIC, "en"))).isEqualTo(french);
    assertThat(read(anon(PUBLIC, ARABIC))).isEqualTo(french);
  }

  @Test
  void theSpecialtyFacetCarriesTheArabicLabel() throws Exception {
    String city = "Arabe" + UUID.randomUUID().toString().substring(0, 8);
    UUID id = account("practitioner", false, "approved");
    jdbc.update(
        "insert into practitioner_profile (practitioner_id, slug, display_name, published, city,"
            + " specialties, modalities, audiences, languages, availability)"
            + " values (?, ?, 'Dr Annuaire', true, ?, '{anxiety}', '{video}', '{adults}', '{fr}',"
            + " 'accepting')",
        id,
        "arabe-" + id.toString().substring(0, 8),
        city);
    JsonNode facet =
        JSON.readTree(read(anon("/api/public/directory?city=" + city, ARABIC)))
            .path("facets")
            .path("specialties");
    JsonNode anxiety = byCode(facet, "anxiety");
    assertThat(anxiety.path("labelAr").asString()).isEqualTo("القلق");
    assertThat(anxiety.path("labelFr").asString()).isEqualTo("Anxiété");
    assertThat(anxiety.path("labelEn").asString()).isEqualTo("Anxiety");
    assertThat(anxiety.path("count").asInt()).isEqualTo(1);
  }

  // ---- administration

  @Test
  void administrationCreatesWithLabelArAndShowsIt() throws Exception {
    RequestPostProcessor admin = admin();
    String code = code();
    insertedCodes.add(code);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("kind", "audience");
    payload.put("code", code);
    payload.put("labelFr", "Personnes âgées");
    payload.put("labelEn", "Older adults");
    payload.put("labelAr", "  كبار السن  ");
    MvcResult created = send(post(ADMIN).with(admin), payload);
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    assertThat(body(created).path("labelAr").asString()).isEqualTo("كبار السن");
    assertThat(storedLabelAr("audience", code)).isEqualTo("كبار السن");

    JsonNode listed =
        byCode(body(mvc.perform(get(ADMIN).with(admin)).andReturn()).path("items"), code);
    assertThat(listed.path("labelAr").asString()).isEqualTo("كبار السن");
    JsonNode shown = byCode(JSON.readTree(read(anon(PUBLIC, ARABIC))).path("audiences"), code);
    assertThat(shown.path("labelAr").asString()).isEqualTo("كبار السن");

    // Vide ou absent à la création : pas de libellé arabe.
    String blank = code();
    insertedCodes.add(blank);
    Map<String, Object> withBlank = new LinkedHashMap<>();
    withBlank.put("kind", "specialty");
    withBlank.put("code", blank);
    withBlank.put("labelFr", "Sans arabe");
    withBlank.put("labelAr", " ");
    MvcResult noArabic = send(post(ADMIN).with(admin), withBlank);
    assertThat(noArabic.getResponse().getStatus()).isEqualTo(201);
    assertThat(body(noArabic).path("labelAr").isNull()).isTrue();
    assertThat(storedLabelAr("specialty", blank)).isNull();
  }

  @Test
  void patchSetsKeepsAndClearsLabelAr() throws Exception {
    RequestPostProcessor admin = admin();
    String code = code();
    insertedCodes.add(code);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("kind", "specialty");
    payload.put("code", code);
    payload.put("labelFr", "Thérapie du sommeil");
    assertThat(send(post(ADMIN).with(admin), payload).getResponse().getStatus()).isEqualTo(201);
    String url = ADMIN + "/specialty/" + code;

    MvcResult set = send(patch(url).with(admin), Map.of("labelAr", "اضطرابات النوم"));
    assertThat(set.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(set).path("labelAr").asString()).isEqualTo("اضطرابات النوم");

    // Champ absent : inchangé.
    MvcResult kept = send(patch(url).with(admin), Map.of("sortOrder", 950));
    assertThat(kept.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(kept).path("labelAr").asString()).isEqualTo("اضطرابات النوم");
    assertThat(storedLabelAr("specialty", code)).isEqualTo("اضطرابات النوم");

    // Chaîne vide : effacé.
    MvcResult cleared = send(patch(url).with(admin), Map.of("labelAr", ""));
    assertThat(cleared.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(cleared).path("labelAr").isNull()).isTrue();
    assertThat(storedLabelAr("specialty", code)).isNull();
    assertThat(body(cleared).path("labelFr").asString()).isEqualTo("Thérapie du sommeil");
  }

  @Test
  void aTooLongLabelArIsRefusedLikeLabelEn() throws Exception {
    RequestPostProcessor admin = admin();
    String code = code();
    insertedCodes.add(code);
    Map<String, Object> tooLong = new LinkedHashMap<>();
    tooLong.put("kind", "specialty");
    tooLong.put("code", code);
    tooLong.put("labelFr", "Libellé");
    tooLong.put("labelEn", "y".repeat(121));
    tooLong.put("labelAr", "ع".repeat(121));
    MvcResult refused = send(post(ADMIN).with(admin), tooLong);
    assertThat(refused.getResponse().getStatus()).isEqualTo(422);
    assertThat(body(refused).path("code").asString()).isEqualTo("VALIDATION_FAILED");
    assertThat(errorFields(refused))
        .containsExactlyInAnyOrder("labelEn:TOO_LONG", "labelAr:TOO_LONG");
    // Même message que pour labelEn.
    JsonNode errors = body(refused).path("errors");
    assertThat(errors.get(0).path("message").asString())
        .isEqualTo(errors.get(1).path("message").asString());
    assertThat(rowCount(code)).isZero();

    // 120 caractères : accepté.
    tooLong.remove("labelEn");
    tooLong.put("labelAr", "ع".repeat(120));
    assertThat(send(post(ADMIN).with(admin), tooLong).getResponse().getStatus()).isEqualTo(201);

    MvcResult patchRefused =
        send(patch(ADMIN + "/specialty/" + code).with(admin), Map.of("labelAr", "ع".repeat(121)));
    assertThat(patchRefused.getResponse().getStatus()).isEqualTo(422);
    assertThat(errorFields(patchRefused)).containsExactly("labelAr:TOO_LONG");
    assertThat(storedLabelAr("specialty", code)).isEqualTo("ع".repeat(120));
  }

  private int rowCount(String code) {
    return jdbc.queryForObject(
        "select count(*) from directory_vocabulary where code = ?", Integer.class, code);
  }
}
