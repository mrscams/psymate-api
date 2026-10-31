package com.psymate.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Schéma de V05 (E4 ligne V05). */
@SpringBootTest
@ActiveProfiles("test")
@Import(ClinicalTestConfig.class)
class ClinicalSchemaIT {

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  private static final List<String> V05_TABLES =
      List.of(
          "entry",
          "entry_version",
          "entry_feeling",
          "entry_medication",
          "entry_session",
          "entry_tag",
          "entry_embedding",
          "entry_chunk");

  @Test
  void v05IsAppliedAndCreatesExactlyTheTablesOfE4() {
    assertThat(
            jdbc.queryForList(
                "select version from flyway_schema_history where success", String.class))
        .contains("05");
    assertThat(
            jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'"
                    + " and (table_name like 'entry\\_%' or table_name = 'entry')"
                    + " and table_name <> 'entry_type'",
                String.class))
        .containsExactlyInAnyOrderElementsOf(V05_TABLES);
    // export_job (V11) et ai_feedback (V07) ne sont pas créées par V05.
    assertThat(
            jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'"
                    + " and table_name in ('export_job', 'ai_feedback', 'link')",
                String.class))
        .isEmpty();
  }

  @Test
  void reservedV2TablesAreEmpty() {
    for (String table : List.of("entry_embedding", "entry_chunk")) {
      assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class))
          .as(table)
          .isZero();
    }
  }

  @Test
  void companionForeignKeysArePlaced() {
    assertThat(
            jdbc.queryForList(
                "select conname from pg_constraint where conname in ('fk_entry_submission',"
                    + " 'fk_submission_session', 'fk_entry_current_version',"
                    + " 'ck_entry_session_required', 'ck_entry_session_not_self',"
                    + " 'ck_entry_occurred_order')",
                String.class))
        .containsExactlyInAnyOrder(
            "fk_entry_submission",
            "fk_submission_session",
            "fk_entry_current_version",
            "ck_entry_session_required",
            "ck_entry_session_not_self",
            "ck_entry_occurred_order");
  }

  @Test
  void expectedIndexesExist() {
    assertThat(
            jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = 'public'", String.class))
        .contains(
            "ix_entry_timeline",
            "ix_entry_timeline_type",
            "ix_entry_session",
            "ix_entry_range",
            "ix_entry_submission",
            "ix_entry_tenant",
            "ux_entry_version",
            "ix_entry_version_title_trgm",
            "ix_entry_version_body_trgm",
            "ix_entry_feeling_emotion",
            "ix_entry_session_open",
            "ix_entry_tag_tag");
  }

  @Test
  void entryVersionIsAppendOnlyForTheAppRole() throws Exception {
    for (String privilege : List.of("UPDATE", "DELETE")) {
      assertThat(
              jdbc.queryForObject(
                  "select has_table_privilege('psymate_app', 'entry_version', ?)",
                  Boolean.class,
                  privilege))
          .as(privilege)
          .isFalse();
    }
    for (String privilege : List.of("SELECT", "INSERT")) {
      assertThat(
              jdbc.queryForObject(
                  "select has_table_privilege('psymate_app', 'entry_version', ?)",
                  Boolean.class,
                  privilege))
          .as(privilege)
          .isTrue();
    }
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("set role psymate_app");
      try {
        for (String sql :
            List.of(
                "update entry_version set title = title where false",
                "delete from entry_version where false")) {
          assertThatThrownBy(() -> statement.execute(sql))
              .as(sql)
              .isInstanceOf(SQLException.class)
              .extracting(e -> ((SQLException) e).getSQLState())
              .isEqualTo("42501");
        }
      } finally {
        statement.execute("reset role");
      }
    }
  }

  @Test
  void sessionRequiredConstraintRejectsAnEntryWithoutSession() {
    var tenant = java.util.UUID.randomUUID();
    var practitioner = java.util.UUID.randomUUID();
    var patient = java.util.UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'T')", tenant);
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession)"
            + " values (?, ?, 'a@b.c', 'a@b.c', 'x', 'A', 'B', 'AB', 'psychologist')",
        practitioner,
        tenant);
    jdbc.update(
        "insert into patient (id, tenant_id, owner_id, display_name) values (?, ?, ?, 'P')",
        patient,
        tenant,
        practitioner);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into entry (id, tenant_id, patient_id, type, occurred_from)"
                        + " values (?, ?, ?, 'note', now())",
                    java.util.UUID.randomUUID(),
                    tenant,
                    patient))
        .hasMessageContaining("ck_entry_session_required");
  }
}
