package com.psymate.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Schéma de V08 : fonctions, triggers, index, rafraîchissement initial, droits du rôle applicatif.
 */
class SearchSchemaIT extends SearchSupport {

  @Test
  void migrationCreatesTheFunctionsTheTriggersAndTheIndex() {
    List<String> functions =
        jdbc.queryForList(
            "select proname from pg_proc where proname in ('entry_search_vector_refresh',"
                + " 'trg_entry_search_vector', 'trg_entry_version_search_vector')",
            String.class);
    assertThat(functions)
        .containsExactlyInAnyOrder(
            "entry_search_vector_refresh",
            "trg_entry_search_vector",
            "trg_entry_version_search_vector");

    List<String> triggers =
        jdbc.queryForList(
            "select tgname from pg_trigger where not tgisinternal and tgname in"
                + " ('entry_search_vector_after_version', 'entry_search_vector_after_grace_edit')",
            String.class);
    assertThat(triggers)
        .containsExactlyInAnyOrder(
            "entry_search_vector_after_version", "entry_search_vector_after_grace_edit");

    String indexDef =
        jdbc.queryForObject(
            "select indexdef from pg_indexes where indexname = 'ix_entry_search'", String.class);
    assertThat(indexDef).containsIgnoringCase("using gin").contains("search_vector");
    assertThat(
            jdbc.queryForObject(
                "select indexdef from pg_indexes where indexname = 'ix_entry_version_text_trgm'",
                String.class))
        .contains("gin_trgm_ops");

    assertThat(
            count(
                "select count(*) from flyway_schema_history where cast(version as int) = 8 and success"))
        .isEqualTo(1);
  }

  @Test
  void theApplicationRoleCanWriteEntriesAndGraceEditsWithTheTriggersFiring() throws Exception {
    // Même droits qu'en production : le trigger tourne avec ceux de psymate_app.
    UUID id = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    DataSource ds = jdbc.getDataSource();
    try (var connection = ds.getConnection()) {
      connection.setAutoCommit(false);
      JdbcTemplate asApp = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      asApp.execute("set local role psymate_app");
      asApp.update(
          "insert into entry (id, tenant_id, patient_id, type, occurred_from, occurred_to,"
              + " session_id, current_version_id, created_by) values (?, ?, ?, 'note', ?, ?, ?,"
              + " ?, ?)",
          id,
          tenant,
          patient,
          Timestamp.from(Instant.now()),
          Timestamp.from(Instant.now()),
          session,
          versionId,
          me);
      asApp.update(
          "insert into entry_version (id, entry_id, version, body, author_id)"
              + " values (?, ?, 1, 'Il évoque la rentrée scolaire', ?)",
          versionId,
          id,
          me);
      assertThat(
              asApp.queryForObject(
                  "select search_vector @@ plainto_tsquery('french', 'rentree') from entry"
                      + " where id = ?",
                  Boolean.class,
                  id))
          .isTrue();
      asApp.update(
          "update entry_version set body = 'Il évoque la retraite' where id = ?", versionId);
      assertThat(
              asApp.queryForObject(
                  "select search_vector @@ plainto_tsquery('french', 'retraite') from entry"
                      + " where id = ?",
                  Boolean.class,
                  id))
          .isTrue();
      connection.commit();
    }
    assertThat(ids(search("q", "retraite"))).containsExactly(id.toString());
  }

  @Test
  void theMigrationRefreshesTheEntriesThatPredateIt() {
    try (PostgreSQLContainer postgres =
        new PostgreSQLContainer(
                DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("psymate")
            .withUsername("psymate")
            .withPassword("psymate")
            .withCopyFileToContainer(
                MountableFile.forHostPath(Path.of("docker/postgres/init/01-app-role.sql")),
                "/docker-entrypoint-initdb.d/01-app-role.sql")) {
      postgres.start();
      DriverManagerDataSource ds =
          new DriverManagerDataSource(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

      // Base à l'état précédant V08 (V07 est livrée par une autre branche : on s'arrête à V06).
      Flyway.configure()
          .dataSource(ds)
          .locations("classpath:db/migration")
          .target("6")
          .load()
          .migrate();
      JdbcTemplate old = new JdbcTemplate(ds);
      UUID tenantId = UUID.randomUUID();
      UUID practitionerId = UUID.randomUUID();
      UUID patientId = UUID.randomUUID();
      UUID sessionId = UUID.randomUUID();
      UUID sessionVersion = UUID.randomUUID();
      UUID noteId = UUID.randomUUID();
      UUID noteVersion = UUID.randomUUID();
      UUID orphanId = UUID.randomUUID();
      old.update("insert into tenant (id, name) values (?, 'Cabinet')", tenantId);
      old.update(
          "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
              + " first_name, last_name, display_name, profession)"
              + " values (?, ?, 'a@example.test', 'a@example.test', 'x', 'A', 'B', 'Dr', 'psychologist')",
          practitionerId,
          tenantId);
      old.update(
          "insert into patient (id, tenant_id, owner_id, display_name, birth_date)"
              + " values (?, ?, ?, 'Patient', date '1990-01-01')",
          patientId,
          tenantId,
          practitionerId);
      // current_version_id posé dès l'INSERT : aucun trigger ne se déclenche, le vecteur reste
      // NULL.
      new org.springframework.transaction.support.TransactionTemplate(
              new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds))
          .executeWithoutResult(
              s -> {
                insertEntry(
                    old,
                    tenantId,
                    patientId,
                    practitionerId,
                    sessionId,
                    sessionVersion,
                    "session",
                    null,
                    null,
                    "Ouverture");
                insertEntry(
                    old,
                    tenantId,
                    patientId,
                    practitionerId,
                    noteId,
                    noteVersion,
                    "note",
                    sessionId,
                    "Licenciement annoncé",
                    "Le licenciement de son frère");
                old.update(
                    "insert into entry (id, tenant_id, patient_id, type, occurred_from,"
                        + " session_id, created_by) values (?, ?, ?, 'note', now(), ?, ?)",
                    orphanId,
                    tenantId,
                    patientId,
                    sessionId,
                    practitionerId);
              });
      assertThat(
              old.queryForObject(
                  "select count(*) from entry where search_vector is null", Integer.class))
          .isEqualTo(3);

      Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

      assertThat(
              old.queryForObject(
                  "select search_vector @@ plainto_tsquery('french', 'licenciement') from entry"
                      + " where id = ?",
                  Boolean.class,
                  noteId))
          .isTrue();
      assertThat(
              old.queryForObject(
                  "select count(*) from entry where search_vector is not null", Integer.class))
          .as("session et note rafraîchies")
          .isEqualTo(2);
      assertThat(
              old.queryForObject(
                  "select search_vector is null from entry where id = ?", Boolean.class, orphanId))
          .as("une entrée sans version courante reste sans vecteur")
          .isTrue();

      // Idempotent : rejouer le rafraîchissement ne change rien.
      String before =
          old.queryForObject(
              "select search_vector::text from entry where id = ?", String.class, noteId);
      old.queryForList(
          "select entry_search_vector_refresh(id) from entry where current_version_id is not null");
      assertThat(
              old.queryForObject(
                  "select search_vector::text from entry where id = ?", String.class, noteId))
          .isEqualTo(before);
    }
  }

  private static void insertEntry(
      JdbcTemplate jdbc,
      UUID tenantId,
      UUID patientId,
      UUID practitionerId,
      UUID entryId,
      UUID versionId,
      String type,
      UUID sessionId,
      String title,
      String body) {
    jdbc.update(
        "insert into entry (id, tenant_id, patient_id, type, occurred_from, session_id,"
            + " current_version_id, created_by) values (?, ?, ?, ?, now(), ?, ?, ?)",
        entryId,
        tenantId,
        patientId,
        type,
        sessionId,
        versionId,
        practitionerId);
    jdbc.update(
        "insert into entry_version (id, entry_id, version, title, body, author_id)"
            + " values (?, ?, 1, ?, ?, ?)",
        versionId,
        entryId,
        title,
        body,
        practitionerId);
  }
}
