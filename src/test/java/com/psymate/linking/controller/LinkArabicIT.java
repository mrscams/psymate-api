package com.psymate.linking.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertArabicGetsFrench;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * M16-02 (G Q96) : liens demandés en arabe. Libellés, phrases et erreurs reçoivent le français tant
 * que l'arabe n'est pas traduit, sans erreur. Même configuration que LinkControllerIT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class LinkArabicIT {

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

  @Test
  void arabicGetsTheFrenchErrorMessages() throws Exception {
    // Validation d'un champ (LinkMessages) : aucun libellé de catalogue dans la réponse.
    String invalid = "{\"targetId\":\"pas-un-uuid\",\"linkType\":\"triggers\"}";
    assertArabicGetsFrench(
        send(MockMvcRequestBuilders.post(links(event)), invalid, "fr")
            .andExpect(status().isUnprocessableContent()),
        send(MockMvcRequestBuilders.post(links(event)), invalid, ARABIC));
    String unknownType = request(feeling, "nope");
    assertArabicGetsFrench(
        send(MockMvcRequestBuilders.post(links(event)), unknownType, "fr")
            .andExpect(status().isUnprocessableContent()),
        send(MockMvcRequestBuilders.post(links(event)), unknownType, ARABIC));
  }

  /**
   * Libellés et phrases en arabe : sans erreur. Leur repli français n'est pas figé ici, M16-03
   * donnant un libellé arabe aux types de lien (G Q98).
   */
  @Test
  void arabicLabelsAndSentencesAreServedWithoutError() throws Exception {
    send(MockMvcRequestBuilders.post(links(note)), request(feeling, "triggers"), ARABIC)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("LINK_TYPE_NOT_ALLOWED"))
        .andExpect(jsonPath("$.allowedLinkTypes[0].label").isNotEmpty());
    send(MockMvcRequestBuilders.post(links(event)), request(feeling, "triggers"), ARABIC)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.label").isNotEmpty())
        .andExpect(jsonPath("$.sentence").isNotEmpty());
    send(MockMvcRequestBuilders.get(links(feeling)), null, ARABIC)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").isNotEmpty());
  }
}
