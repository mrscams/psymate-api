package com.psymate.identity.auth;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Postgres (pgvector) + Mailpit + horloge contrôlée pour AuthIT. */
@TestConfiguration(proxyBeanMethods = false)
class AuthTestConfig {

  /** Horloge modifiable, partagée par l'application et le test. */
  static class MutableClock extends Clock {
    private volatile Instant now = Instant.now();

    void advance(Duration d) {
      now = now.plus(d);
    }

    void reset() {
      now = Instant.now();
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

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

  @Bean
  @Primary
  MutableClock testClock() {
    return new MutableClock();
  }
}
