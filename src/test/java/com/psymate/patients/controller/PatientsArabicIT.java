package com.psymate.patients.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertArabicGetsFrench;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
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
 * M16-02 (G Q96) : dossiers et Profil 360 demandés en arabe. Les erreurs (ProblemMessages,
 * PatientProfileMessages) reçoivent le français tant que l'arabe n'est pas traduit, sans erreur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class PatientsArabicIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID patient;
  private RequestPostProcessor auth;

  @BeforeEach
  void practitionerAndPatient() {
    UUID tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    UUID me = UUID.randomUUID();
    String email = me + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist')",
        me,
        tenant,
        email,
        email);
    patient = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'P')",
        patient,
        tenant,
        me);
    CurrentPractitioner principal =
        new CurrentPractitioner(me, tenant, email, "Dr Test", "psychologist", "none");
    auth =
        authentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions in(String language, Supplier<MockHttpServletRequestBuilder> request)
      throws Exception {
    return mvc.perform(request.get().header("Accept-Language", language).with(auth));
  }

  private Supplier<MockHttpServletRequestBuilder> postJson(String url, String json) {
    return () -> post(url).contentType(MediaType.APPLICATION_JSON).content(json);
  }

  private String items() {
    return "/api/patients/" + patient + "/profile-items";
  }

  @Test
  void arabicProfileErrorsGetTheFrenchMessages() throws Exception {
    String missingItem = items() + "/" + UUID.randomUUID();
    Supplier<MockHttpServletRequestBuilder> missing = () -> delete(missingItem);
    assertArabicGetsFrench(
        in("fr", missing)
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.detail").value("Information de profil introuvable pour ce patient.")),
        in(ARABIC, missing));
    Supplier<MockHttpServletRequestBuilder> unknownField =
        postJson(items(), "{\"category\":\"family\",\"title\":\"T\",\"authorType\":\"x\"}");
    assertArabicGetsFrench(
        in("fr", unknownField).andExpect(status().isUnprocessableContent()),
        in(ARABIC, unknownField));
    Supplier<MockHttpServletRequestBuilder> noTitle =
        postJson(items(), "{\"category\":\"family\"}");
    assertArabicGetsFrench(
        in("fr", noTitle).andExpect(status().isUnprocessableContent()), in(ARABIC, noTitle));
  }

  @Test
  void arabicPatientErrorsAndReadsAnswerLikeFrenchOnes() throws Exception {
    Supplier<MockHttpServletRequestBuilder> malformed = postJson("/api/patients", "{nope");
    assertArabicGetsFrench(
        in("fr", malformed).andExpect(status().isBadRequest()), in(ARABIC, malformed));
    Supplier<MockHttpServletRequestBuilder> noName =
        postJson("/api/patients", "{\"firstName\":\"X\"}");
    assertArabicGetsFrench(
        in("fr", noName).andExpect(status().isUnprocessableEntity()), in(ARABIC, noName));
    Supplier<MockHttpServletRequestBuilder> read = () -> get("/api/patients/" + patient);
    assertArabicGetsFrench(in("fr", read).andExpect(status().isOk()), in(ARABIC, read));
    Supplier<MockHttpServletRequestBuilder> profile = () -> get(items());
    assertArabicGetsFrench(in("fr", profile).andExpect(status().isOk()), in(ARABIC, profile));
  }
}
