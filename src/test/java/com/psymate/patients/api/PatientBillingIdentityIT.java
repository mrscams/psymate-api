package com.psymate.patients.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.testsupport.SharedPostgres;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link PatientFacade#billingIdentity} et {@link PatientFacade#billingIdentities} (M15-04, G Q85)
 * : ce que la facturation doit savoir d'un patient, sur base réelle, la façade obtenue par son
 * interface comme le ferait un autre module.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class PatientBillingIdentityIT {

  @Autowired private PatientFacade facade;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory emf;

  private UUID tenant;
  private UUID owner;

  @BeforeEach
  void data() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    owner = UUID.randomUUID();
    String email = owner + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'psychologist')",
        owner,
        tenant,
        email,
        email);
  }

  /**
   * Patient complet, avec un suivi qui porte un tarif et un prescripteur, un contexte et un
   * téléphone : rien de cela ne doit sortir.
   */
  private UUID patient(String firstName, String lastName, String email, boolean archived) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name, first_name, last_name, email,"
            + " phone, context, follow_up, archived_at)"
            + " values (?, ?, ?, ?, ?, ?, ?, '0601020304', '{\"origin\":\"adressé\"}'::jsonb,"
            + " '{\"fee\":\"70 €\",\"prescriber\":\"Dr Martin\"}'::jsonb, "
            + (archived ? "now()" : "null")
            + ")",
        id,
        tenant,
        owner,
        firstName + " " + lastName.charAt(0) + ".",
        firstName,
        lastName,
        email);
    return id;
  }

  @Test
  void activePatientGivesTheSevenFields() {
    UUID id = patient("Théo", "Bernard", "theo@example.test", false);

    assertThat(facade.billingIdentity(id))
        .contains(
            new PatientBillingView(
                id, owner, "Théo B.", "Théo", "Bernard", "theo@example.test", false));
  }

  @Test
  void archivedPatientGivesTheSevenFieldsWithArchivedTrue() {
    UUID id = patient("Lina", "Moreau", "lina@example.test", true);

    assertThat(facade.billingIdentity(id))
        .contains(
            new PatientBillingView(
                id, owner, "Lina M.", "Lina", "Moreau", "lina@example.test", true));
  }

  @Test
  void patientWithoutNamesNorEmailGivesNulls() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'Anonyme')",
        id,
        tenant,
        owner);

    assertThat(facade.billingIdentity(id))
        .contains(new PatientBillingView(id, owner, "Anonyme", null, null, null, false));
  }

  @Test
  void unknownPatientIsEmpty() {
    assertThat(facade.billingIdentity(UUID.randomUUID())).isEmpty();
  }

  @Test
  void batchGivesActiveAndArchivedPatientsAndIgnoresUnknownIds() {
    UUID active = patient("Théo", "Bernard", "theo@example.test", false);
    UUID archived = patient("Lina", "Moreau", "lina@example.test", true);
    UUID unknown = UUID.randomUUID();

    Map<UUID, PatientBillingView> views =
        facade.billingIdentities(Arrays.asList(active, unknown, archived, active, null));

    assertThat(views)
        .containsOnlyKeys(active, archived)
        .containsEntry(
            active,
            new PatientBillingView(
                active, owner, "Théo B.", "Théo", "Bernard", "theo@example.test", false))
        .containsEntry(
            archived,
            new PatientBillingView(
                archived, owner, "Lina M.", "Lina", "Moreau", "lina@example.test", true));
  }

  @Test
  void batchRunsASingleQueryForNIds() {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      ids.add(patient("Prénom" + i, "Nom" + i, "p" + i + "@example.test", i % 3 == 0));
    }
    ids.add(UUID.randomUUID());

    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    try {
      long before = stats.getPrepareStatementCount();
      Map<UUID, PatientBillingView> views = facade.billingIdentities(ids);
      assertThat(stats.getPrepareStatementCount() - before).as("requêtes").isEqualTo(1);
      assertThat(views).hasSize(12);
      assertThat(views.values()).filteredOn(PatientBillingView::archived).hasSize(4);
    } finally {
      stats.setStatisticsEnabled(false);
    }
  }

  @Test
  void emptyBatchGivesAnEmptyMapWithoutQuery() {
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    try {
      long before = stats.getPrepareStatementCount();
      assertThat(facade.billingIdentities(List.of())).isEmpty();
      assertThat(stats.getPrepareStatementCount() - before).isZero();
    } finally {
      stats.setStatisticsEnabled(false);
    }
  }

  @Test
  void viewHasExactlySevenComponentsAndNoFollowUpNorClinicalData() {
    assertThat(PatientBillingView.class.getRecordComponents())
        .extracting(RecordComponent::getName)
        .containsExactly(
            "id", "ownerId", "displayName", "firstName", "lastName", "email", "archived");
  }
}
