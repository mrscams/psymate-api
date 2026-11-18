package com.psymate.patients.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.patients.PatientServiceFixtures;
import com.psymate.patients.model.PatientProfileCategory;
import com.psymate.patients.model.PatientProfileItem;
import com.psymate.testsupport.SharedPostgres;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Table patient_profile_item (V14, J2) : contraintes SQL de rubrique, de période et d'année, clés
 * étrangères, et mapping JPA de l'entité (ddl-auto=validate au démarrage du contexte de test).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class PatientProfilePersistenceIT {

  @Autowired private PatientProfileItemRepository items;
  @Autowired private JdbcTemplate jdbc;

  private PatientServiceFixtures fx;
  private UUID alice;
  private UUID patient;

  @BeforeEach
  void data() {
    fx = new PatientServiceFixtures(jdbc);
    alice = fx.newPractitioner();
    patient = insertPatient();
  }

  private UUID insertPatient() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'P')",
        id,
        fx.tenant,
        alice);
    return id;
  }

  private int insert(String category, String periodLabel, Integer periodYear) {
    return insert(patient, category, periodLabel, periodYear);
  }

  private int insert(UUID patientId, String category, String periodLabel, Integer periodYear) {
    return jdbc.update(
        "insert into patient_profile_item (id, tenant_id, patient_id, category, title,"
            + " period_label, period_year, created_by) values (?, ?, ?, ?, 'Titre', ?, ?, ?)",
        UUID.randomUUID(),
        fx.tenant,
        patientId,
        category,
        periodLabel,
        periodYear,
        alice);
  }

  @Test
  void aHistoryItemAcceptsAPeriodAndOtherCategoriesAcceptNone() {
    assertThat(insert("history", "Enfance", 1998)).isEqualTo(1);
    assertThat(insert("history", null, null)).isEqualTo(1);
    for (String category : new String[] {"family", "health", "values_goals"}) {
      assertThat(insert(category, null, null)).as(category).isEqualTo(1);
    }
  }

  @Test
  void ckPatientProfileItemPeriodRefusesAPeriodOutsideHistory() {
    for (String category : new String[] {"family", "health", "values_goals"}) {
      assertThatThrownBy(() -> insert(category, "Enfance", null))
          .as(category + " avec periodLabel")
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_patient_profile_item_period");
      assertThatThrownBy(() -> insert(category, null, 1998))
          .as(category + " avec periodYear")
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("ck_patient_profile_item_period");
    }
  }

  @Test
  void anUnknownCategoryIsRefused() {
    assertThatThrownBy(() -> insert("resources", null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("patient_profile_item_category_check");
  }

  @Test
  void aYearOutside1900To2100IsRefused() {
    assertThat(insert("history", null, 1900)).isEqualTo(1);
    assertThat(insert("history", null, 2100)).isEqualTo(1);
    for (int year : new int[] {1899, 2101}) {
      assertThatThrownBy(() -> insert("history", null, year))
          .as(String.valueOf(year))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("patient_profile_item_period_year_check");
    }
  }

  @Test
  void anUnknownPatientIsRefusedByTheForeignKey() {
    assertThatThrownBy(() -> insert(UUID.randomUUID(), "family", null, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("patient_profile_item_patient_id_fkey");
  }

  @Test
  void theEntityRoundTripsAndActiveQueriesSkipArchivedItems() {
    UUID other = insertPatient();
    PatientProfileItem kept =
        items.save(
            new PatientProfileItem(
                UUID.randomUUID(),
                fx.tenant,
                patient,
                PatientProfileCategory.values_goals,
                "Reprendre le piano",
                "Projet évoqué en septembre",
                null,
                null,
                alice,
                PatientProfileItem.now()));
    PatientProfileItem gone =
        new PatientProfileItem(
            UUID.randomUUID(),
            fx.tenant,
            patient,
            PatientProfileCategory.history,
            "Déménagement à Lyon",
            null,
            "Vers 2000",
            2000,
            alice,
            PatientProfileItem.now());
    gone.archive(PatientProfileItem.now());
    items.save(gone);

    PatientProfileItem read = items.findById(kept.getId()).orElseThrow();
    assertThat(read.getCategory()).isEqualTo(PatientProfileCategory.values_goals);
    assertThat(read.getTitle()).isEqualTo("Reprendre le piano");
    assertThat(read.getDetail()).isEqualTo("Projet évoqué en septembre");
    assertThat(read.getCreatedAt()).isEqualTo(kept.getCreatedAt());
    assertThat(items.findById(gone.getId()).orElseThrow().getPeriodYear()).isEqualTo(2000);

    assertThat(items.findByPatientIdAndArchivedAtIsNull(patient))
        .extracting(PatientProfileItem::getId)
        .containsExactly(kept.getId());
    assertThat(items.countByPatientIdAndArchivedAtIsNull(patient)).isEqualTo(1);
    assertThat(items.findByIdAndPatientId(gone.getId(), patient)).isPresent();
    assertThat(items.findByIdAndPatientId(kept.getId(), other)).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select category from patient_profile_item where id = ?",
                String.class,
                kept.getId()))
        .isEqualTo("values_goals");
  }
}
