package com.psymate.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.vocabulary.EmotionService.MergeView;
import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.TagView;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Le module vocabulary tourne ici avec le rôle psymate_app (Flyway avec le propriétaire). Ce module
 * n'a aucune table append-only : ce n'est pas un test de refus (comme PatientAppRoleIT pour
 * patient_version) mais un test d'écriture autorisée. Chaque INSERT et UPDATE émis par Hibernate
 * sur tag, emotion et emotion_merge, et les écritures natives sur patient_tag, passent avec les
 * seuls droits du rôle applicatif.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(VocabularyAppRoleIT.AppRoleConfig.class)
class VocabularyAppRoleIT {

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
  }

  @Autowired private TagService tags;
  @Autowired private EmotionService emotions;
  @Autowired private JdbcTemplate jdbc;

  private VocabularyTestFixtures fx;
  private UUID alice;

  @BeforeEach
  void practitioner() {
    assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("psymate_app");
    fx = new VocabularyTestFixtures(jdbc);
    alice = fx.newPractitioner();
    fx.loginAs(alice);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void theApplicationRoleCanSelectInsertAndUpdateTheModuleTables() {
    for (String table : List.of("tag", "emotion", "emotion_merge")) {
      for (String privilege : List.of("SELECT", "INSERT", "UPDATE")) {
        assertThat(
                jdbc.queryForObject(
                    "select has_table_privilege('psymate_app', ?, ?)",
                    Boolean.class,
                    table,
                    privilege))
            .as(table + " " + privilege)
            .isTrue();
      }
    }
  }

  @Test
  void aTagIsCreatedThenRenamedUnderTheApplicationRole() {
    TagView created = tags.create("Premier");
    assertThat(created.label()).isEqualTo("Premier");
    assertThat(created.usageCount()).isZero();

    TagView renamed = tags.rename(created.id(), "Second");

    assertThat(renamed.label()).isEqualTo("Second");
    assertThat(renamed.normalizedLabel()).isEqualTo("second");
    assertThat(
            jdbc.queryForObject("select label from tag where id = ?", String.class, created.id()))
        .isEqualTo("Second");
  }

  @Test
  void aCustomEmotionIsCreatedMergedThenUndoneUnderTheApplicationRole() {
    EmotionView created = emotions.create("Lassitude", "sadness");
    assertThat(created.scope()).isEqualTo("custom");
    assertThat(created.active()).isTrue();

    MergeView merge = emotions.merge(created.code(), "sadness");
    assertThat(emotionActive(created.code())).isFalse();
    assertThat(merge.undoUntil()).isEqualTo(merge.mergedAt().plusDays(30));

    MergeView undone = emotions.undo(merge.id());
    assertThat(undone.undoneAt()).isNotNull();
    assertThat(emotionActive(created.code())).isTrue();

    EmotionView renamed = emotions.rename(created.code(), "Fatigue");
    assertThat(renamed.labelFr()).isEqualTo("Fatigue");
  }

  @Test
  void twoTagsAreMergedWithTheirPatientTagsUnderTheApplicationRole() {
    UUID source = fx.newTag(alice, "Boulot");
    UUID target = fx.newTag(alice, "Travail");
    UUID p1 = fx.newPatient(alice);
    UUID p2 = fx.newPatient(alice);
    jdbc.update("insert into patient_tag (patient_id, tag_id) values (?, ?)", p1, source);
    jdbc.update("insert into patient_tag (patient_id, tag_id) values (?, ?)", p2, source);
    jdbc.update("insert into patient_tag (patient_id, tag_id) values (?, ?)", p2, target);
    jdbc.update("update tag set usage_count = 2 where id = ?", source);

    TagView merged = tags.merge(source, target);

    assertThat(merged.id()).isEqualTo(target);
    assertThat(merged.usageCount()).isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                "select patient_id from patient_tag where tag_id = ?", UUID.class, target))
        .containsExactlyInAnyOrder(p1, p2);
    assertThat(
            jdbc.queryForObject(
                "select archived_at is not null from tag where id = ?", Boolean.class, source))
        .isTrue();
  }

  private boolean emotionActive(String code) {
    return jdbc.queryForObject("select active from emotion where code = ?", Boolean.class, code);
  }
}
