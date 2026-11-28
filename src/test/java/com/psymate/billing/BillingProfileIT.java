package com.psymate.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.testsupport.SharedPostgres;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
 * Profil de facturation d'un patient (M15-05, L5.1) sur une vraie base (Testcontainers
 * pgvector/pgvector:pg16) : lecture d'un profil vide, écriture, effacement par null, 404 pour le
 * patient d'un autre praticien, 409 pour un patient archivé, 422 par champ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class BillingProfileIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenant;
  private UUID alice;
  private UUID bob;
  private UUID theo;
  private UUID bobsPatient;

  @BeforeEach
  void fixtures() {
    SecurityContextHolder.clearContext();
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet facturation')", tenant);
    alice = practitioner();
    bob = practitioner();
    theo = patient(alice, "Théo B.");
    bobsPatient = patient(bob, "Patient de Bob");
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void emptyProfileReadsAsNulls() throws Exception {
    get(alice, theo)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rate").value(nullValue()))
        .andExpect(jsonPath("$.addressLines").value(nullValue()))
        .andExpect(jsonPath("$.thirdPartyName").value(nullValue()))
        .andExpect(jsonPath("$.thirdPartyAddress").value(nullValue()));
    assertThat(rows(theo)).isZero();
  }

  @Test
  void putStoresTheProfileInCentsAndGetReadsIt() throws Exception {
    put(
            alice,
            theo,
            "{\"rate\":5500,\"addressLines\":\"  12 rue des Lilas\\n75011 Paris  \","
                + "\"thirdPartyName\":\"Mutuelle Exemple\",\"thirdPartyAddress\":\"BP 12\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rate").value(5500))
        .andExpect(jsonPath("$.addressLines").value("12 rue des Lilas\n75011 Paris"))
        .andExpect(jsonPath("$.thirdPartyName").value("Mutuelle Exemple"))
        .andExpect(jsonPath("$.thirdPartyAddress").value("BP 12"));

    get(alice, theo)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rate").value(5500))
        .andExpect(jsonPath("$.thirdPartyName").value("Mutuelle Exemple"));
    Map<String, Object> row =
        jdbc.queryForMap(
            "select tenant_id, practitioner_id, rate_cents from billing_patient where patient_id = ?",
            theo);
    assertThat(row.get("tenant_id")).isEqualTo(tenant);
    assertThat(row.get("practitioner_id")).isEqualTo(alice);
    assertThat(row.get("rate_cents")).isEqualTo(5500);
  }

  @Test
  void putReplacesEverythingAndNullAbsentOrBlankClear() throws Exception {
    put(alice, theo, "{\"rate\":5500,\"addressLines\":\"Paris\",\"thirdPartyName\":\"M\"}")
        .andExpect(status().isOk());

    put(alice, theo, "{\"rate\":null,\"addressLines\":\"Lyon\",\"thirdPartyName\":\"   \"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rate").value(nullValue()))
        .andExpect(jsonPath("$.addressLines").value("Lyon"))
        .andExpect(jsonPath("$.thirdPartyName").value(nullValue()));

    put(alice, theo, "{}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.addressLines").value(nullValue()));
    // Le profil effacé garde sa ligne : rien n'est supprimé.
    assertThat(rows(theo)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select rate_cents is null and address_lines is null and third_party_name is null"
                    + " from billing_patient where patient_id = ?",
                Boolean.class,
                theo))
        .isTrue();
  }

  @Test
  void anotherPractitionersPatientIsNotFound() throws Exception {
    get(alice, bobsPatient)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    put(alice, bobsPatient, "{\"rate\":5500}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    get(alice, UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    assertThat(rows(bobsPatient)).isZero();
  }

  @Test
  void archivedPatientIsReadableButNotWritable() throws Exception {
    put(alice, theo, "{\"rate\":5500}").andExpect(status().isOk());
    jdbc.update("update patient set archived_at = now() where id = ?", theo);

    put(alice, theo, "{\"rate\":6000}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PATIENT_ARCHIVED"))
        .andExpect(jsonPath("$.detail").isNotEmpty());
    get(alice, theo).andExpect(status().isOk()).andExpect(jsonPath("$.rate").value(5500));
  }

  @Test
  void zeroNegativeDecimalOrTextRatesAreRefused() throws Exception {
    expectFieldError(put(alice, theo, "{\"rate\":0}"), "rate", "OUT_OF_RANGE");
    expectFieldError(put(alice, theo, "{\"rate\":-1}"), "rate", "OUT_OF_RANGE");
    expectFieldError(put(alice, theo, "{\"rate\":55.5}"), "rate", "INVALID_FORMAT");
    expectFieldError(put(alice, theo, "{\"rate\":\"5500\"}"), "rate", "INVALID_FORMAT");
    assertThat(rows(theo)).isZero();
  }

  @Test
  void longTextsAndUnknownFieldsAreRefused() throws Exception {
    expectFieldError(
        put(alice, theo, "{\"addressLines\":\"" + "x".repeat(301) + "\"}"),
        "addressLines",
        "OUT_OF_RANGE");
    expectFieldError(
        put(alice, theo, "{\"thirdPartyName\":\"" + "x".repeat(201) + "\"}"),
        "thirdPartyName",
        "OUT_OF_RANGE");
    expectFieldError(
        put(alice, theo, "{\"patientId\":\"" + theo + "\"}"), "patientId", "UNKNOWN_FIELD");
    expectFieldError(
        put(alice, theo, "{\"practitionerId\":\"" + bob + "\"}"),
        "practitionerId",
        "UNKNOWN_FIELD");
    assertThat(rows(theo)).isZero();
  }

  @Test
  void malformedPatientIdIs400() throws Exception {
    send(MockMvcRequestBuilders.get("/api/patients/pas-un-uuid/billing-profile"), alice, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  // ---- outils

  private UUID practitioner() {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Facturation', 'Dr Facturation',"
            + " 'psychologist')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  private UUID patient(UUID owner, String displayName) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, ?)",
        id,
        tenant,
        owner,
        displayName);
    return id;
  }

  private int rows(UUID patientId) {
    return jdbc.queryForObject(
        "select count(*) from billing_patient where patient_id = ?", Integer.class, patientId);
  }

  private static void expectFieldError(ResultActions result, String field, String code)
      throws Exception {
    result
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].code").value(code))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
  }

  private RequestPostProcessor as(UUID practitionerId) {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitionerId,
            tenant,
            practitionerId + "@example.test",
            "Dr Facturation",
            "psychologist",
            "none");
    return authentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private ResultActions send(MockHttpServletRequestBuilder builder, UUID who, String body)
      throws Exception {
    if (body != null) {
      builder
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.getBytes(StandardCharsets.UTF_8));
    }
    return mvc.perform(builder.with(as(who)));
  }

  private ResultActions get(UUID who, UUID patientId) throws Exception {
    return send(
        MockMvcRequestBuilders.get("/api/patients/" + patientId + "/billing-profile"), who, null);
  }

  private ResultActions put(UUID who, UUID patientId, String body) throws Exception {
    return send(
        MockMvcRequestBuilders.put("/api/patients/" + patientId + "/billing-profile"), who, body);
  }
}
