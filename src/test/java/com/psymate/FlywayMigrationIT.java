package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.yaml.snakeyaml.Yaml;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class FlywayMigrationIT {

  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  @Test
  void v01IsAppliedOnEmptyDatabase() {
    assertThat(
            jdbc()
                .queryForList(
                    "select version from flyway_schema_history where success order by installed_rank",
                    String.class))
        .contains("01");
  }

  @Test
  @SuppressWarnings("unchecked")
  void tablesAndColumnsMatchExpectedSchema() throws Exception {
    Map<String, List<String>> expected;
    try (InputStream in = getClass().getResourceAsStream("/expected-schema-v01.yaml")) {
      expected = new TreeMap<>((Map<String, List<String>>) new Yaml().load(in));
    }

    Map<String, List<String>> actual = new TreeMap<>();
    jdbc()
        .query(
            """
            select table_name, column_name from information_schema.columns
            where table_schema = 'public' and table_name <> 'flyway_schema_history'
            order by table_name, ordinal_position
            """,
            rs -> {
              actual
                  .computeIfAbsent(rs.getString(1), k -> new java.util.ArrayList<>())
                  .add(rs.getString(2));
            });

    assertThat(actual.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
    expected.forEach((table, cols) -> assertThat(actual.get(table)).as(table).isEqualTo(cols));
  }

  @Test
  void expectedIndexesExist() {
    List<String> indexes =
        jdbc()
            .queryForList(
                "select indexname from pg_indexes where schemaname = 'public'", String.class);
    assertThat(indexes)
        .contains(
            "ux_practitioner_email",
            "ix_practitioner_tenant",
            "ix_practitioner_verification",
            "ux_auth_token_hash",
            "ix_auth_token_active",
            "ix_totp_recovery_practitioner",
            "spring_session_ix1",
            "spring_session_ix2",
            "spring_session_ix3",
            "ix_security_event_practitioner",
            "ix_security_event_kind",
            "ix_patient_owner",
            "ix_patient_name_trgm",
            "ux_patient_version",
            "ux_patient_flag_active",
            "ux_patient_access_active",
            "ux_emotion_custom_label",
            "ux_emotion_global_label",
            "ix_emotion_practitioner",
            "event_publication_serialized_event_hash_idx",
            "event_publication_by_completion_date_idx",
            "ix_patient_device_patient",
            "ix_submission_patient_pending",
            "ux_tag_practitioner_label",
            "ix_patient_tag_tag",
            "ix_export_job_practitioner",
            "ix_export_job_purge");
  }

  @Test
  void reservedCompanionTablesExistAndAreEmpty() {
    for (String table : List.of("patient_device", "patient_submission")) {
      assertThat(jdbc().queryForObject("select count(*) from " + table, Integer.class))
          .as(table)
          .isZero();
    }
  }

  /**
   * Compte exact du vocabulaire de référence de V02, sur toute la table : la base de ce test est
   * dédiée, aucun autre test n'y écrit (VocabularyIT ne vérifie que les codes du seed).
   */
  @Test
  void v02SeedsExactlySixEntryTypesAndEightLinkTypes() {
    assertThat(jdbc().queryForObject("select count(*) from entry_type", Integer.class))
        .isEqualTo(6);
    assertThat(jdbc().queryForObject("select count(*) from link_type", Integer.class)).isEqualTo(8);
  }

  @Test
  void extensionsAreInstalled() {
    assertThat(jdbc().queryForList("select extname from pg_extension", String.class))
        .contains("pg_trgm", "unaccent", "vector", "btree_gist", "pgcrypto");
  }

  @Test
  void appRoleCannotUpdateOrDeleteAppendOnlyTables() {
    for (String table : List.of("security_event", "patient_version")) {
      for (String privilege : List.of("UPDATE", "DELETE")) {
        assertThat(
                jdbc()
                    .queryForObject(
                        "select has_table_privilege('psymate_app', ?, ?)",
                        Boolean.class,
                        table,
                        privilege))
            .as(table + " " + privilege)
            .isFalse();
      }
      for (String privilege : List.of("SELECT", "INSERT")) {
        assertThat(
                jdbc()
                    .queryForObject(
                        "select has_table_privilege('psymate_app', ?, ?)",
                        Boolean.class,
                        table,
                        privilege))
            .as(table + " " + privilege)
            .isTrue();
      }
    }
  }

  /** E4 « Vérifications en CI » : une écriture réelle sous psymate_app doit être refusée. */
  @Test
  void appRoleUpdateAndDeleteAreRefused() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("set role psymate_app");
      try {
        for (String table : List.of("security_event", "patient_version")) {
          for (String sql :
              List.of(
                  "update " + table + " set id = id where false",
                  "delete from " + table + " where false")) {
            assertThatThrownBy(() -> statement.execute(sql))
                .as(sql)
                .isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState())
                .isEqualTo("42501");
          }
        }
      } finally {
        statement.execute("reset role");
      }
    }
  }

  @Test
  void v03CreatesExactlyTheFourVerificationTables() {
    assertThat(
            jdbc()
                .queryForList(
                    "select version from flyway_schema_history where success order by"
                        + " installed_rank",
                    String.class))
        .contains("03");
    assertThat(
            jdbc()
                .queryForList(
                    "select table_name from information_schema.tables where table_schema ="
                        + " 'public' and table_name in ('verification_document',"
                        + " 'verification_decision', 'document_access_log',"
                        + " 'practitioner_appeal')",
                    String.class))
        .containsExactlyInAnyOrder(
            "verification_document",
            "verification_decision",
            "document_access_log",
            "practitioner_appeal");
  }

  @Test
  void v03AppendOnlyTablesRefuseUpdateAndDeleteForAppRole() throws Exception {
    for (String table : List.of("verification_decision", "document_access_log")) {
      for (String privilege : List.of("UPDATE", "DELETE")) {
        assertThat(
                jdbc()
                    .queryForObject(
                        "select has_table_privilege('psymate_app', ?, ?)",
                        Boolean.class,
                        table,
                        privilege))
            .as(table + " " + privilege)
            .isFalse();
      }
      for (String privilege : List.of("SELECT", "INSERT")) {
        assertThat(
                jdbc()
                    .queryForObject(
                        "select has_table_privilege('psymate_app', ?, ?)",
                        Boolean.class,
                        table,
                        privilege))
            .as(table + " " + privilege)
            .isTrue();
      }
    }
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("set role psymate_app");
      try {
        for (String table : List.of("verification_decision", "document_access_log")) {
          assertThatThrownBy(
                  () -> statement.execute("update " + table + " set id = id where false"))
              .isInstanceOf(SQLException.class)
              .extracting(e -> ((SQLException) e).getSQLState())
              .isEqualTo("42501");
          assertThatThrownBy(() -> statement.execute("delete from " + table + " where false"))
              .isInstanceOf(SQLException.class)
              .extracting(e -> ((SQLException) e).getSQLState())
              .isEqualTo("42501");
        }
      } finally {
        statement.execute("reset role");
      }
    }
  }

  /** V10 (M1-14b) : une vue en lecture seule, ni table ni autre vue ; pas d'ip dans la vue. */
  @Test
  void v10CreatesOnlyTheAdminJournalView() {
    assertThat(
            jdbc()
                .queryForList(
                    "select version from flyway_schema_history where success order by"
                        + " installed_rank",
                    String.class))
        .contains("10");
    assertThat(
            jdbc()
                .queryForList(
                    "select table_name from information_schema.views where table_schema ="
                        + " 'public'",
                    String.class))
        .contains("admin_journal_v");
    assertThat(
            jdbc()
                .queryForList(
                    "select table_name from information_schema.tables where table_schema ="
                        + " 'public' and table_type = 'BASE TABLE' and table_name like"
                        + " 'admin_journal%'",
                    String.class))
        .isEmpty();
    assertThat(
            jdbc()
                .queryForList(
                    "select column_name from information_schema.columns where table_schema ="
                        + " 'public' and table_name = 'admin_journal_v'",
                    String.class))
        .doesNotContain("ip", "user_agent");
  }

  /** V07 (M2-01) : link et ai_feedback, index d'unicité partiel et garde contre l'auto-lien. */
  @Test
  void v07CreatesLinkAndEmptyAiFeedbackWithTheActiveLinkUniqueIndex() {
    assertThat(
            jdbc()
                .queryForList(
                    "select version from flyway_schema_history where success order by"
                        + " installed_rank",
                    String.class))
        .contains("07");
    assertThat(jdbc().queryForObject("select count(*) from ai_feedback", Integer.class)).isZero();
    assertThat(
            jdbc()
                .queryForList(
                    "select indexname from pg_indexes where schemaname = 'public'"
                        + " and tablename = 'link'",
                    String.class))
        .contains("ux_link_active", "ix_link_source", "ix_link_target", "ix_link_patient");
    assertThat(
            jdbc()
                .queryForObject(
                    "select indexdef from pg_indexes where indexname = 'ux_link_active'",
                    String.class))
        .startsWith("CREATE UNIQUE INDEX")
        .contains("source_id, target_id, link_type")
        .contains("proposed")
        .contains("accepted");
    assertThat(
            jdbc()
                .queryForList(
                    "select conname from pg_constraint c join pg_class t on t.oid = c.conrelid"
                        + " where t.relname = 'link' and c.contype = 'c'",
                    String.class))
        .contains("ck_link_not_self");
  }
}
