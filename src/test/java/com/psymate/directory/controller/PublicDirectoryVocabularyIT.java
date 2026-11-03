package com.psymate.directory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M10-01 : GET /api/public/directory/vocabulary rend, sans session, le contenu de GET
 * /api/vocabulary/directory (praticien), sous la limite publique de 60 requêtes par minute et par
 * IP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class PublicDirectoryVocabularyIT {

  private static final String PUBLIC = "/api/public/directory/vocabulary";
  private static final String PRACTITIONER = "/api/vocabulary/directory";
  private static final List<String> LISTS =
      List.of("specialties", "audiences", "modalities", "languages");
  private static final AtomicInteger IP = new AtomicInteger();

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  private final JsonMapper json = JsonMapper.builder().build();

  private UUID tenant;

  /** Codes insérés par le test en cours : désactivés en fin de test, même après un échec. */
  private final List<String> insertedCodes = new ArrayList<>();

  @AfterEach
  void deactivateInsertedCodes() {
    for (String c : insertedCodes) {
      jdbc.update("update directory_vocabulary set active = false where code = ?", c);
    }
  }

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  /**
   * Chaque appel de test vient d'une IP distincte (préfixe 10.81.) : le seau de débit est partagé.
   */
  private static String freshIp() {
    int n = IP.incrementAndGet();
    return "10.81." + (n / 250) + "." + (n % 250 + 1);
  }

  private static MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder b) {
    return b.with(
        r -> {
          r.setRemoteAddr(ip);
          return r;
        });
  }

  private MockHttpServletRequestBuilder anon() {
    return from(freshIp(), get(PUBLIC));
  }

  private MockHttpServletRequestBuilder asPractitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, email_confirmed_at,"
            + " password_hash, first_name, last_name, display_name, profession,"
            + " verification_status, role) values (?, ?, ?, ?, now(), 'x', 'Test', 'Compte',"
            + " 'Dr Vocabulaire', 'psychologist', 'approved', 'practitioner')",
        id,
        tenant,
        email,
        email);
    CurrentPractitioner principal =
        new CurrentPractitioner(id, tenant, email, "Dr Test", "psychologist", "approved");
    return from(freshIp(), get(PRACTITIONER))
        .with(
            authentication(
                UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_PRACTITIONER")))));
  }

  private static String code() {
    return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private static List<String> codes(JsonNode list) {
    List<String> out = new ArrayList<>();
    list.forEach(n -> out.add(n.path("code").asString()));
    return out;
  }

  @Test
  void anonymousGets200WithTheSameCodesAndLabelsAsThePractitionerEndpoint() throws Exception {
    MvcResult pub = mvc.perform(anon()).andExpect(status().isOk()).andReturn();
    MvcResult pro = mvc.perform(asPractitioner()).andExpect(status().isOk()).andReturn();

    JsonNode publicBody = body(pub);
    // Mêmes listes, mêmes codes, mêmes libellés FR et EN, même ordre.
    assertThat(publicBody).isEqualTo(body(pro));
    assertThat(codes(publicBody.path("modalities"))).containsExactly("in_person", "video", "home");
    assertThat(codes(publicBody.path("languages"))).hasSize(15).contains("fr", "en");
    assertThat(codes(publicBody.path("specialties"))).contains("anxiety");
    assertThat(codes(publicBody.path("audiences"))).contains("adults");
    for (JsonNode item : publicBody.path("specialties")) {
      assertThat(item.path("labelFr").asString()).isNotBlank();
      assertThat(item.has("labelEn")).isTrue();
    }
  }

  /** L'endpoint praticien est conservé tel quel : toujours réservé aux sessions. */
  @Test
  void thePractitionerEndpointStillRequiresASession() throws Exception {
    mvc.perform(from(freshIp(), get(PRACTITIONER))).andExpect(status().isUnauthorized());
  }

  @Test
  void answersWithoutSessionAndWithAPublicCacheHeader() throws Exception {
    mvc.perform(anon())
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist("Set-Cookie"))
        .andExpect(header().string("Cache-Control", Matchers.containsString("max-age=60")))
        .andExpect(header().string("Cache-Control", Matchers.containsString("public")))
        .andExpect(content().contentTypeCompatibleWith("application/json"));
  }

  @Test
  void aSpecialtyOrAudienceDeactivatedByTheAdminAppearsInNeitherResponse() throws Exception {
    String specialty = code();
    String audience = code();
    insertedCodes.addAll(List.of(specialty, audience));
    jdbc.update(
        "insert into directory_vocabulary (kind, code, label_fr, label_en) values"
            + " ('specialty', ?, 'Spécialité éphémère', 'Ephemeral specialty'),"
            + " ('audience', ?, 'Public éphémère', 'Ephemeral audience')",
        specialty,
        audience);
    assertThat(codes(body(mvc.perform(anon()).andReturn()).path("specialties")))
        .contains(specialty);
    assertThat(codes(body(mvc.perform(asPractitioner()).andReturn()).path("audiences")))
        .contains(audience);

    jdbc.update(
        "update directory_vocabulary set active = false where code in (?, ?)", specialty, audience);

    JsonNode publicBody = body(mvc.perform(anon()).andReturn());
    JsonNode proBody = body(mvc.perform(asPractitioner()).andReturn());
    for (JsonNode b : List.of(publicBody, proBody)) {
      assertThat(codes(b.path("specialties"))).doesNotContain(specialty);
      assertThat(codes(b.path("audiences"))).doesNotContain(audience);
    }
  }

  @Test
  void theResponseCarriesTheFourVocabularyListsAndNothingElse() throws Exception {
    MvcResult r = mvc.perform(anon()).andExpect(status().isOk()).andReturn();
    JsonNode b = body(r);
    List<String> fields = new ArrayList<>();
    b.propertyNames().forEach(fields::add);
    assertThat(fields).containsExactlyInAnyOrderElementsOf(LISTS);
    for (String list : LISTS) {
      for (JsonNode item : b.path(list)) {
        List<String> itemFields = new ArrayList<>();
        item.propertyNames().forEach(itemFields::add);
        assertThat(itemFields).containsExactlyInAnyOrder("code", "labelFr", "labelEn");
      }
    }
  }

  @Test
  void sixtyFirstRequestFromSameIpIsRateLimited() throws Exception {
    String ip = freshIp();
    for (int i = 0; i < 60; i++) {
      mvc.perform(from(ip, get(PUBLIC))).andExpect(status().isOk());
    }
    mvc.perform(from(ip, get(PUBLIC)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    // Une autre adresse n'est pas touchée.
    mvc.perform(anon()).andExpect(status().isOk());
  }
}
