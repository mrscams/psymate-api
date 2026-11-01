package com.psymate.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Schéma de V09 : la table patient_overview de E3.9, ses contraintes et les droits applicatifs. */
class OverviewSchemaIT extends OverviewSupport {

  @Test
  void migrationCreatesExactlyThePatientOverviewTable() {
    Map<String, String> columns = new LinkedHashMap<>();
    Map<String, String> nullable = new LinkedHashMap<>();
    jdbc.query(
        "select column_name, data_type, is_nullable from information_schema.columns"
            + " where table_schema = 'public' and table_name = 'patient_overview'"
            + " order by ordinal_position",
        rs -> {
          columns.put(rs.getString(1), rs.getString(2));
          nullable.put(rs.getString(1), rs.getString(3));
        });

    assertThat(columns)
        .containsExactly(
            Map.entry("patient_id", "uuid"),
            Map.entry("payload", "jsonb"),
            Map.entry("source_rev", "bigint"),
            Map.entry("computed_at", "timestamp with time zone"),
            Map.entry("compute_ms", "integer"));
    assertThat(nullable)
        .containsExactly(
            Map.entry("patient_id", "NO"),
            Map.entry("payload", "NO"),
            Map.entry("source_rev", "NO"),
            Map.entry("computed_at", "NO"),
            Map.entry("compute_ms", "YES"));
    assertThat(
            jdbc.queryForObject(
                "select column_default from information_schema.columns where table_name ="
                    + " 'patient_overview' and column_name = 'computed_at'",
                String.class))
        .isEqualTo("now()");
  }

  @Test
  void patientIdIsThePrimaryKeyAndReferencesPatient() {
    assertThat(
            jdbc.queryForList(
                "select a.attname from pg_index i join pg_attribute a on a.attrelid = i.indrelid"
                    + " and a.attnum = any(i.indkey) where i.indrelid = 'patient_overview'::regclass"
                    + " and i.indisprimary",
                String.class))
        .containsExactly("patient_id");
    assertThat(
            jdbc.queryForObject(
                "select confrelid::regclass::text from pg_constraint"
                    + " where conrelid = 'patient_overview'::regclass and contype = 'f'",
                String.class))
        .isEqualTo("patient");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into patient_overview (patient_id, payload, source_rev)"
                        + " values (?, '{}'::jsonb, 0)",
                    UUID.randomUUID()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void migrationNineIsApplied() {
    assertThat(
            count(
                "select count(*) from flyway_schema_history where cast(version as int) = 9"
                    + " and success"))
        .isEqualTo(1);
  }

  @Test
  void theApplicationRoleCanReadAndWritePatientOverview() throws Exception {
    DataSource ds = jdbc.getDataSource();
    try (var connection = ds.getConnection()) {
      connection.setAutoCommit(false);
      JdbcTemplate asApp = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      asApp.execute("set local role psymate_app");
      asApp.update(
          "insert into patient_overview (patient_id, payload, source_rev, compute_ms)"
              + " values (?, '{\"facts\":{}}'::jsonb, 3, 12)",
          patient);
      asApp.update(
          "update patient_overview set payload = '{\"facts\":{\"a\":1}}'::jsonb, source_rev = 4,"
              + " computed_at = ? where patient_id = ?",
          Timestamp.from(Instant.now()),
          patient);
      assertThat(
              asApp.queryForObject(
                  "select source_rev from patient_overview where patient_id = ?",
                  Long.class,
                  patient))
          .isEqualTo(4L);
      assertThat(
              asApp.queryForObject(
                  "select payload->'facts'->>'a' from patient_overview where patient_id = ?",
                  String.class,
                  patient))
          .isEqualTo("1");
      connection.commit();
    }
    assertThat(count("select count(*) from patient_overview where patient_id = ?", patient))
        .isEqualTo(1);
  }
}
