package com.psymate.vocabulary;

import java.nio.file.Path;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Conteneur pgvector (comme PostgresTestConfig, package-private dans com.psymate) et praticien
 * courant modifiable pour les tests du module vocabulary.
 */
@TestConfiguration(proxyBeanMethods = false)
class VocabularyTestConfig {

  @Bean
  @ServiceConnection
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
  SwitchablePractitioner currentPractitionerPort() {
    return new SwitchablePractitioner();
  }

  /** Port de test : le praticien courant se change d'un test à l'autre. */
  static class SwitchablePractitioner implements CurrentPractitionerPort {

    private volatile UUID practitionerId;
    private volatile UUID tenantId;

    void use(UUID tenant, UUID practitioner) {
      this.tenantId = tenant;
      this.practitionerId = practitioner;
    }

    @Override
    public UUID practitionerId() {
      return practitionerId;
    }

    @Override
    public UUID tenantId() {
      return tenantId;
    }
  }
}
