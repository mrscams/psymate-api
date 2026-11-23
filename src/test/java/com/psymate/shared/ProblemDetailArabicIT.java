package com.psymate.shared;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertArabicGetsFrench;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * M16-02 (G Q96) : erreurs transverses demandées en arabe. Détails ({@code ProblemMessages}) et
 * messages par défaut de Jakarta Validation ({@code ServedLocaleResolver}) reçoivent le français,
 * sans erreur. Même configuration que ProblemDetailIT, dont les routes de test sont reprises.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SharedPostgres.class, ProblemDetailIT.Endpoints.class})
class ProblemDetailArabicIT {

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

  private ResultActions perform(MockHttpServletRequestBuilder b, String language) throws Exception {
    return mvc.perform(b.header("Accept-Language", language).with(auth));
  }

  @Test
  void transverseErrorsInArabicGetTheFrenchDetails() throws Exception {
    assertArabicGetsFrench(
        perform(get("/api/nope"), "fr")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Ressource introuvable.")),
        perform(get("/api/nope"), ARABIC));
    assertArabicGetsFrench(
        perform(get("/api/test-problems/param"), "fr").andExpect(status().isBadRequest()),
        perform(get("/api/test-problems/param"), ARABIC));
    assertArabicGetsFrench(
        perform(
                post("/api/patients").contentType(MediaType.APPLICATION_JSON).content("{nope"),
                "fr")
            .andExpect(status().isBadRequest()),
        perform(
            post("/api/patients").contentType(MediaType.APPLICATION_JSON).content("{nope"),
            ARABIC));
    assertArabicGetsFrench(
        mvc.perform(get("/api/patients").header("Accept-Language", "fr"))
            .andExpect(status().isUnauthorized()),
        mvc.perform(get("/api/patients").header("Accept-Language", ARABIC)));
  }

  /**
   * Messages par défaut de la validation : ceux d'Hibernate Validator en français, pas en arabe.
   */
  @Test
  void validationDefaultMessagesInArabicAreTheFrenchOnes() throws Exception {
    String body = "{\"name\":\"\",\"age\":5}";
    assertArabicGetsFrench(
        perform(
                post("/api/test-problems/valid")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
                "fr")
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.errors.length()").value(1)),
        perform(
            post("/api/test-problems/valid").contentType(MediaType.APPLICATION_JSON).content(body),
            ARABIC));
  }
}
