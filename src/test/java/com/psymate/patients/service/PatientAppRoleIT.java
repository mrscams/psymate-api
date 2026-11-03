package com.psymate.patients.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.patients.PatientServiceFixtures;
import com.psymate.patients.dto.CreatePatientRequest;
import com.psymate.patients.dto.FlagRequest;
import com.psymate.patients.dto.VersionDto;
import com.psymate.testsupport.AppRolePostgres;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Le module patients tourne ici avec le rôle psymate_app (Flyway avec le propriétaire) : les
 * écritures JPA passent là où le rôle a le droit, et patient_version reste append-only (REVOKE
 * UPDATE, DELETE) : Hibernate n'émet jamais d'UPDATE ni de DELETE dessus (entité @Immutable).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AppRolePostgres.class)
@DirtiesContext
class PatientAppRoleIT {

  @Autowired private PatientService service;
  @Autowired private JdbcTemplate jdbc;

  private PatientServiceFixtures fx;
  private UUID alice;

  @BeforeEach
  void practitioner() {
    assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("psymate_app");
    fx = new PatientServiceFixtures(jdbc);
    alice = fx.newPractitioner();
    fx.loginAs(alice);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  /**
   * Crée le patient et attend la séance d'accueil que clinical écrit en asynchrone (clinical_rev).
   */
  private UUID create(String name) {
    UUID id =
        service
            .create(
                new CreatePatientRequest(
                    name, null, null, null, null, null, null, null, null, null, null))
            .id();
    fx.awaitIntakeSession(id);
    return id;
  }

  @Test
  void createThenThreePatchesReadThreeVersionsUnderTheApplicationRole() {
    UUID id = create("Marie D.");
    service.patch(id, Map.of("phone", "0102"));
    service.patch(id, Map.of("followUp", Map.of("rhythm", "weekly"), "reason", "Nouveau rythme"));
    service.patch(id, Map.of("displayName", "Marie Dupont"));

    List<VersionDto> versions = service.versions(id);
    assertThat(versions).extracting(VersionDto::version).containsExactly(2, 3, 4);
    assertThat(versions.get(0).snapshot()).containsEntry("phone", "0102");
    assertThat(versions.get(1).reason()).isEqualTo("Nouveau rythme");
    assertThat(versions.get(2).snapshot()).containsEntry("displayName", "Marie Dupont");
    assertThat(versions).extracting(VersionDto::authorId).containsOnly(alice);
    assertThat(service.get(id).currentVersion()).isEqualTo(4);
  }

  @Test
  void patientVersionIsAppendOnlyForTheApplicationRole() {
    for (String privilege : List.of("UPDATE", "DELETE")) {
      assertThat(
              jdbc.queryForObject(
                  "select has_table_privilege('psymate_app', 'patient_version', ?)",
                  Boolean.class,
                  privilege))
          .as(privilege)
          .isFalse();
    }
    for (String privilege : List.of("SELECT", "INSERT")) {
      assertThat(
              jdbc.queryForObject(
                  "select has_table_privilege('psymate_app', 'patient_version', ?)",
                  Boolean.class,
                  privilege))
          .as(privilege)
          .isTrue();
    }
    UUID id = create("Append only");
    service.patch(id, Map.of("phone", "0102"));
    assertThatThrownBy(
            () -> jdbc.update("update patient_version set reason = 'x' where patient_id = ?", id))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied");
    assertThatThrownBy(() -> jdbc.update("delete from patient_version where patient_id = ?", id))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied");
    assertThat(service.versions(id)).hasSize(1);
  }

  @Test
  void touchClinicalRevAddsOneAndArchiveRestoreKeepWorking() {
    UUID id = create("Rev");
    long base = clinicalRev(id);

    service.touchClinicalRev(id);
    assertThat(clinicalRev(id)).isEqualTo(base + 1);

    service.archive(id);
    service.restore(id);
    service.patch(id, Map.of("phone", "0102"));
    assertThat(clinicalRev(id))
        .as("aucun UPDATE d'entité n'écrit clinical_rev")
        .isEqualTo(base + 1);
    assertThat(service.get(id).archivedAt()).isNull();
  }

  @Test
  void flagsAndTagsAreWrittenUpdatedAndDeletedWithTheRolePrivileges() {
    UUID id = create("Flags");
    fx.newTag(alice, "sommeil");
    service.addFlag(id, new FlagRequest("risk", null));
    assertThat(service.get(id).flags()).hasSize(1);
    service.clearFlag(id, "risk");
    assertThat(service.get(id).flags()).isEmpty();
    service.addFlag(id, new FlagRequest("risk", null));
    assertThat(service.get(id).flags()).hasSize(1);

    service.replaceTags(id, List.of("sommeil"));
    assertThat(service.get(id).tags()).hasSize(1);
    service.replaceTags(id, List.of());
    assertThat(service.get(id).tags()).isEmpty();
  }

  private long clinicalRev(UUID id) {
    return jdbc.queryForObject("select clinical_rev from patient where id = ?", Long.class, id);
  }
}
