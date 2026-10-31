package com.psymate.directory;

import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Postgres (pgvector) + Mailpit pour les tests du formulaire de contact. */
@TestConfiguration(proxyBeanMethods = false)
class ContactTestConfig {

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
  GenericContainer<?> mailpit() {
    return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.32.0"))
        .withExposedPorts(1025, 8025)
        .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));
  }

  @Bean
  DynamicPropertyRegistrar mailProperties(GenericContainer<?> mailpit) {
    return registry -> {
      registry.add("spring.mail.host", mailpit::getHost);
      registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    };
  }
}
