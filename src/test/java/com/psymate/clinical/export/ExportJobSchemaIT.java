package com.psymate.clinical.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.PostgresTestConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * V11 (M6-05) : table export_job, CHECK sur le statut, clé étrangère, index, droits applicatifs.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class ExportJobSchemaIT {

  @Autowired private JdbcTemplate jdbc;

  private UUID practitioner() {
    UUID tenant = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet schema')", tenant);
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

  @Test
  void v11IsAppliedAndTheTableHasItsColumnsInOrder() {
    assertThat(
            jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank",
                String.class))
        .contains("11");
    assertThat(
            jdbc.queryForList(
                "select column_name from information_schema.columns where table_name ="
                    + " 'export_job' order by ordinal_position",
                String.class))
        .containsExactly(
            "id",
            "practitioner_id",
            "status",
            "requested_at",
            "finished_at",
            "storage_key",
            "expires_at",
            "purged_at",
            "error");
  }

  @Test
  void aNewRowIsQueuedWithARequestDate() {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into export_job (id, practitioner_id) values (?, ?)", id, practitioner());
    assertThat(jdbc.queryForObject("select status from export_job where id = ?", String.class, id))
        .isEqualTo("queued");
    assertThat(
            jdbc.queryForObject(
                "select requested_at is not null and purged_at is null and expires_at is null"
                    + " from export_job where id = ?",
                Boolean.class,
                id))
        .isTrue();
  }

  @Test
  void statusIsRestrictedToTheFourKnownValues() {
    UUID practitioner = practitioner();
    for (String ok : List.of("queued", "running", "done", "failed")) {
      jdbc.update(
          "insert into export_job (id, practitioner_id, status) values (?, ?, ?)",
          UUID.randomUUID(),
          practitioner,
          ok);
    }
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into export_job (id, practitioner_id, status) values (?, ?, 'expired')",
                    UUID.randomUUID(),
                    practitioner))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void thePractitionerMustExist() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into export_job (id, practitioner_id) values (?, ?)",
                    UUID.randomUUID(),
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void indexesExistAndThePurgeIndexIsPartial() {
    assertThat(
            jdbc.queryForObject(
                "select indexdef from pg_indexes where indexname = 'ix_export_job_practitioner'",
                String.class))
        .contains("practitioner_id", "requested_at DESC");
    String purge =
        jdbc.queryForObject(
            "select indexdef from pg_indexes where indexname = 'ix_export_job_purge'",
            String.class);
    assertThat(purge).contains("expires_at").contains("purged_at IS NULL").contains("'done'");
  }

  @Test
  void theApplicationRoleCanReadInsertAndUpdate() {
    // Pas de GRANT dans V11 : les droits viennent des privilèges par défaut du script d'init.
    for (String privilege : List.of("SELECT", "INSERT", "UPDATE")) {
      assertThat(
              jdbc.queryForObject(
                  "select has_table_privilege('psymate_app', 'export_job', ?)",
                  Boolean.class,
                  privilege))
          .as(privilege)
          .isTrue();
    }
  }
}
