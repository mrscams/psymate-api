package com.psymate.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.clinical.application.EntryService;
import com.psymate.identity.api.CurrentPractitioner;
import java.time.Instant;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ClinicalTestConfig.class)
class IntakeSessionIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntryService service;

  private ClinicalFixtures fx;

  @BeforeEach
  void setUp() {
    fx = new ClinicalFixtures(jdbc);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  private String createPatientThroughApi() throws Exception {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            fx.practitioner, fx.tenant, "x@example.test", "Dr Test", "psychologist", "none");
    String res =
        mvc.perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"displayName\":\"Marie D.\"}")
                    .with(
                        authentication(
                            UsernamePasswordAuthenticationToken.authenticated(
                                principal, null, List.of()))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return res.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
  }

  private int intakeCount(String patientId) {
    return jdbc.queryForObject(
        "select count(*) from entry e join entry_session s on s.entry_id = e.id"
            + " where e.patient_id = ?::uuid and s.kind = 'intake'",
        Integer.class,
        patientId);
  }

  private void awaitIntake(String patientId) throws InterruptedException {
    for (int i = 0; i < 100 && intakeCount(patientId) == 0; i++) {
      Thread.sleep(100);
    }
  }

  @Test
  void creatingAPatientProducesAnIntakeSession() throws Exception {
    String patientId = createPatientThroughApi();
    awaitIntake(patientId);

    assertThat(intakeCount(patientId)).isEqualTo(1);
    Map<String, Object> row =
        jdbc.queryForMap(
            "select e.type, e.session_id, e.occurred_to, e.occurred_precision, e.author_type,"
                + " e.submission_id, e.created_by, e.occurred_from, p.created_at as patient_created,"
                + " s.closed_at, s.modality, v.version"
                + " from entry e join entry_session s on s.entry_id = e.id"
                + " join entry_version v on v.id = e.current_version_id"
                + " join patient p on p.id = e.patient_id"
                + " where e.patient_id = ?::uuid and s.kind = 'intake'",
            patientId);
    assertThat(row.get("type")).isEqualTo("session");
    assertThat(row.get("session_id")).isNull();
    assertThat(row.get("occurred_to")).isNull();
    assertThat(row.get("closed_at")).as("la séance d'accueil est ouverte").isNull();
    assertThat(row.get("author_type")).isEqualTo("practitioner");
    assertThat(row.get("submission_id")).isNull();
    assertThat(row.get("created_by")).isEqualTo(fx.practitioner);
    assertThat(row.get("version")).isEqualTo(1);
    // occurred_from = création du patient (même instant à la milliseconde près, côté événement)
    Instant from = ((java.sql.Timestamp) row.get("occurred_from")).toInstant();
    Instant created = ((java.sql.Timestamp) row.get("patient_created")).toInstant();
    assertThat(java.time.Duration.between(created, from).abs())
        .isLessThan(java.time.Duration.ofSeconds(5));
  }

  @Test
  void intakeCreationIsIdempotentAndSkipsAlreadyProvidedPatient() throws Exception {
    String patientId = createPatientThroughApi();
    awaitIntake(patientId);
    UUID patient = UUID.fromString(patientId);
    long revBefore =
        jdbc.queryForObject("select clinical_rev from patient where id = ?", Long.class, patient);

    // Redélivrance de l'événement : le patient est déjà pourvu.
    service.createIntakeSession(fx.tenant, patient, fx.practitioner, Instant.now());
    service.createIntakeSession(fx.tenant, patient, fx.practitioner, Instant.now());

    assertThat(intakeCount(patientId)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select clinical_rev from patient where id = ?", Long.class, patient))
        .isEqualTo(revBefore);
  }

  @Test
  void unknownPatientGetsNoIntakeSession() {
    UUID ghost = UUID.randomUUID();
    service.createIntakeSession(fx.tenant, ghost, fx.practitioner, Instant.now());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from entry where patient_id = ?", Integer.class, ghost))
        .isZero();
  }
}
