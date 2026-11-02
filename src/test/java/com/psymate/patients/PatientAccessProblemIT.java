package com.psymate.patients;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Corps exact des refus de la garde de propriété (M9-04) sur un endpoint de chaque module
 * consommateur : la {@code PatientAccessException} est rendue par le seul handler commun, avec le
 * même statut, le même titre, le même détail et le même code que les traductions recopiées qu'elle
 * remplace, quels que soient {@code Accept} et {@code Accept-Language}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PatientTestConfig.class)
class PatientAccessProblemIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;
  private UUID me;
  private UUID colleaguePatient;

  @BeforeEach
  void data() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    me = practitioner();
    UUID colleague = practitioner();
    colleaguePatient = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'X')",
        colleaguePatient,
        tenant,
        colleague);
  }

  /** patients, clinical (web et export), linking, search, overview. */
  @ParameterizedTest
  @ValueSource(
      strings = {"", "/timeline", "/export?format=md", "/graph", "/search?q=test", "/overview"})
  void anotherPractitionersPatientIs403PatientForbidden(String suffix) throws Exception {
    for (MockHttpServletRequestBuilder request : variants(colleaguePatient, suffix)) {
      mvc.perform(request.with(auth()))
          .andExpect(status().isForbidden())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(
              content()
                  .json(
                      problem(
                          403,
                          "Forbidden",
                          "Ce patient n'est pas le vôtre.",
                          path(colleaguePatient, suffix),
                          "PATIENT_FORBIDDEN"),
                      JsonCompareMode.STRICT));
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "/timeline", "/export?format=md", "/graph", "/search?q=test", "/overview"})
  void anUnknownPatientIs404PatientNotFound(String suffix) throws Exception {
    UUID unknown = UUID.randomUUID();
    for (MockHttpServletRequestBuilder request : variants(unknown, suffix)) {
      mvc.perform(request.with(auth()))
          .andExpect(status().isNotFound())
          .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(
              content()
                  .json(
                      problem(
                          404,
                          "Not Found",
                          "Patient introuvable.",
                          path(unknown, suffix),
                          "PATIENT_NOT_FOUND"),
                      JsonCompareMode.STRICT));
    }
  }

  /** Sans en-tête, en anglais, et avec {@code Accept: application/json}. */
  private static List<MockHttpServletRequestBuilder> variants(UUID patient, String suffix) {
    String url = "/api/patients/" + patient + suffix;
    return List.of(
        get(url),
        get(url).header("Accept-Language", "en"),
        get(url).accept(MediaType.APPLICATION_JSON));
  }

  private static String path(UUID patient, String suffix) {
    int query = suffix.indexOf('?');
    return "/api/patients/" + patient + (query < 0 ? suffix : suffix.substring(0, query));
  }

  private static String problem(
      int status, String title, String detail, String instance, String code) {
    return """
        {"title":"%s","status":%d,"detail":"%s","instance":"%s","code":"%s"}"""
        .formatted(title, status, detail, instance, code);
  }

  private RequestPostProcessor auth() {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            me, tenant, me + "@example.test", "Dr Test", "psychologist", "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private UUID practitioner() {
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
}
