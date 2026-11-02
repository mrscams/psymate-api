package com.psymate.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.clinical.ClinicalFixtures;
import com.psymate.clinical.ClinicalTestConfig.MutableClock;
import com.psymate.clinical.model.EntryType;
import com.psymate.clinical.model.SessionDetail;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * L'application tourne ici avec le rôle psymate_app (Flyway avec le propriétaire) : la correction
 * en période de grâce doit passer, et le trigger trg_entry_version_grace borner le reste (G Q31).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(GraceRuleAppRoleIT.AppRoleConfig.class)
class GraceRuleAppRoleIT {

  /** Fenêtre de grâce : 1 h 15 (versioning.grace-period = 75m). */
  private static final Duration WITHIN_GRACE = Duration.ofMinutes(74);

  private static final Duration AFTER_GRACE = Duration.ofMinutes(76);

  @TestConfiguration(proxyBeanMethods = false)
  static class AppRoleConfig {
    @Bean(destroyMethod = "stop", initMethod = "start")
    PostgreSQLContainer postgres() {
      return new PostgreSQLContainer(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("psymate")
          .withUsername("psymate")
          .withPassword("psymate")
          .withCopyFileToContainer(
              MountableFile.forHostPath(Path.of("docker/postgres/init/01-app-role.sql")),
              "/docker-entrypoint-initdb.d/01-app-role.sql");
    }

    @Bean
    DynamicPropertyRegistrar roles(PostgreSQLContainer c) {
      return registry -> {
        registry.add("spring.datasource.url", c::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "psymate_app");
        registry.add("spring.datasource.password", () -> "psymate_app");
        registry.add("spring.flyway.url", c::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "psymate");
        registry.add("spring.flyway.password", () -> "psymate");
      };
    }

    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired private EntryService service;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;
  @Autowired private PostgreSQLContainer container;

  private ClinicalFixtures fx;
  private UUID patient;
  private UUID session;

  @BeforeEach
  void setUp() {
    fx = new ClinicalFixtures(jdbc);
    patient = fx.newPatient(null);
    clock.set(Instant.now());
    session =
        service.create(
            patient,
            new CreateEntryCommand(
                EntryType.SESSION,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new SessionDetail(null, "in_person", null, null)));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private UUID note(String body) {
    return service.create(
        patient,
        new CreateEntryCommand(
            EntryType.NOTE, session, null, body, null, null, null, null, null, null));
  }

  private static CorrectEntryCommand body(String body) {
    return new CorrectEntryCommand(null, body, null, null, null, null, null, null, null);
  }

  private JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(container.getJdbcUrl(), "psymate", "psymate"));
  }

  @Test
  void graceCorrectionWorksUnderTheApplicationRole() {
    assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("psymate_app");
    UUID id = note("coquile");
    clock.advance(WITHIN_GRACE);

    CorrectionResult r = service.correct(id, body("coquille"));

    assertThat(r.versioned()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select body from entry_version where entry_id = ?", String.class, id))
        .isEqualTo("coquille");
  }

  @Test
  void versionedCorrectionStillWorksUnderTheApplicationRole() {
    UUID id = note("v1");
    clock.advance(AFTER_GRACE);
    assertThat(service.correct(id, body("v2")).version()).isEqualTo(2);
  }

  @Test
  void triggerRefusesUpdatingVersionTwoAndFrozenVersionOne() {
    UUID id = note("v1");
    clock.advance(AFTER_GRACE);
    service.correct(id, body("v2"));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update entry_version set body = 'x' where entry_id = ? and version = 2", id))
        .hasMessageContaining("seule la version 1");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update entry_version set body = 'x' where entry_id = ? and version = 1", id))
        .hasMessageContaining("version suivante");
  }

  @Test
  void triggerRefusesLinkedEntryAndTooOldVersion() {
    UUID linked = note("a");
    owner().update("update entry set link_count = 1 where id = ?", linked);
    assertThatThrownBy(
            () -> jdbc.update("update entry_version set body = 'x' where entry_id = ?", linked))
        .hasMessageContaining("liée");

    clock.set(Instant.now().minus(Duration.ofHours(3)));
    UUID old = note("b");
    clock.set(Instant.now());
    assertThatThrownBy(
            () -> jdbc.update("update entry_version set body = 'x' where entry_id = ?", old))
        .hasMessageContaining("hors fenêtre");
  }

  @Test
  void updatingOtherColumnsIsRefusedByPrivilegeAndByTrigger() {
    UUID id = note("c");
    // Rôle applicatif : pas de privilège sur les autres colonnes.
    assertThatThrownBy(
            () -> jdbc.update("update entry_version set reason = 'x' where entry_id = ?", id))
        .rootCause()
        .hasMessageContaining("permission denied");
    // Propriétaire (qui a le privilège) : le trigger refuse quand même.
    assertThatThrownBy(
            () -> owner().update("update entry_version set reason = 'x' where entry_id = ?", id))
        .hasMessageContaining("seuls title, body et payload");
    assertThatThrownBy(() -> jdbc.update("delete from entry_version where entry_id = ?", id))
        .rootCause()
        .hasMessageContaining("permission denied");
  }
}
