package com.psymate.linking.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.psymate.clinical.model.EntryType;
import com.psymate.clinical.service.EntryService;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.linking.LinkingFixtures;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * M16-03 (G Q98) : verbe d'un lien rendu selon la langue. Arabe : le libellé arabe du type (inverse
 * vu depuis la cible), le français à défaut ; français et anglais inchangés. Même configuration que
 * LinkArabicIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class LinkArabicLabelsIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntryService entries;

  private LinkingFixtures fx;
  private RequestPostProcessor me;
  private UUID event;
  private UUID feeling;
  private UUID note;

  @BeforeEach
  void setUp() {
    fx = new LinkingFixtures(jdbc, entries);
    UUID patient = fx.newPatient();
    UUID session = fx.session(patient);
    event = fx.entry(patient, session, EntryType.EVENT);
    feeling = fx.entry(patient, session, EntryType.FEELING);
    note = fx.entry(patient, session, EntryType.NOTE);
    SecurityContextHolder.clearContext();
    CurrentPractitioner principal =
        new CurrentPractitioner(
            fx.practitioner,
            fx.tenant,
            fx.practitioner + "@example.test",
            "Dr Test",
            "psychologist",
            "none");
    me =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  private ResultActions send(MockHttpServletRequestBuilder builder, String body, String lang)
      throws Exception {
    if (body != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.getBytes(StandardCharsets.UTF_8));
    }
    return mvc.perform(builder.header("Accept-Language", lang).with(me));
  }

  private static String links(UUID entryId) {
    return "/api/entries/" + entryId + "/links";
  }

  private static String request(UUID targetId, String linkType) {
    return "{\"targetId\":\"%s\",\"linkType\":\"%s\",\"direction\":\"outgoing\"}"
        .formatted(targetId, linkType);
  }

  private String label(UUID entryId, String lang) throws Exception {
    String body =
        send(MockMvcRequestBuilders.get(links(entryId)), null, lang)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return JsonPath.read(body, "$[0].label");
  }

  @Test
  void theVerbIsArabicFromTheSourceAndItsInverseFromTheTarget() throws Exception {
    String created =
        send(MockMvcRequestBuilders.post(links(event)), request(feeling, "triggers"), ARABIC)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.label").value("يُثير"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(JsonPath.<String>read(created, "$.sentence")).contains(" يُثير ");
    assertThat(label(event, ARABIC)).isEqualTo("يُثير");
    assertThat(label(feeling, ARABIC)).isEqualTo("يُثيره");
  }

  @Test
  void frenchAndEnglishVerbsAreUnchanged() throws Exception {
    send(MockMvcRequestBuilders.post(links(event)), request(feeling, "triggers"), "fr")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.label").value("déclenche"));
    assertThat(label(event, "fr")).isEqualTo("déclenche");
    assertThat(label(feeling, "fr")).isEqualTo("déclenché par");
    assertThat(label(event, "en")).isEqualTo("triggers");
    assertThat(label(feeling, "en")).isEqualTo("triggered by");
  }

  @Test
  void aLinkTypeWithoutArabicLabelGivesTheFrenchVerb() throws Exception {
    send(MockMvcRequestBuilders.post(links(event)), request(feeling, "triggers"), "fr")
        .andExpect(status().isCreated());
    String labelAr =
        jdbc.queryForObject("select label_ar from link_type where code = 'triggers'", String.class);
    String inverseAr =
        jdbc.queryForObject(
            "select inverse_label_ar from link_type where code = 'triggers'", String.class);
    jdbc.update(
        "update link_type set label_ar = null, inverse_label_ar = null where code = 'triggers'");
    try {
      assertThat(label(event, ARABIC)).isEqualTo("déclenche");
      assertThat(label(feeling, ARABIC)).isEqualTo("déclenché par");
    } finally {
      jdbc.update(
          "update link_type set label_ar = ?, inverse_label_ar = ? where code = 'triggers'",
          labelAr,
          inverseAr);
    }
  }

  /** LINK_TYPE_NOT_ALLOWED : les types admis sont nommés dans la langue demandée. */
  @Test
  void allowedLinkTypesOfAnErrorAreNamedInArabic() throws Exception {
    String body =
        send(MockMvcRequestBuilders.post(links(note)), request(feeling, "triggers"), ARABIC)
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("LINK_TYPE_NOT_ALLOWED"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    List<String> codes = JsonPath.read(body, "$.allowedLinkTypes[*].code");
    List<String> labels = JsonPath.read(body, "$.allowedLinkTypes[*].label");
    assertThat(codes).contains("co_occurs");
    assertThat(labels.get(codes.indexOf("co_occurs"))).isEqualTo("يتزامن مع");

    String french =
        send(MockMvcRequestBuilders.post(links(note)), request(feeling, "triggers"), "fr")
            .andExpect(status().isUnprocessableContent())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    List<String> frenchCodes = JsonPath.read(french, "$.allowedLinkTypes[*].code");
    List<String> frenchLabels = JsonPath.read(french, "$.allowedLinkTypes[*].label");
    assertThat(frenchLabels.get(frenchCodes.indexOf("co_occurs"))).isEqualTo("survient avec");
  }
}
