package com.psymate.identity.admin.bootstrap;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Postgres (pgvector), horloge contrôlée et enregistreur de sorties de processus pour les tests du
 * compte admin de bootstrap. Pas de Mailpit : aucun e-mail n'est envoyé.
 */
@TestConfiguration(proxyBeanMethods = false)
class BootstrapTestConfig {

  /** Horloge modifiable : le code TOTP d'un pas de 30 s ne peut servir qu'une fois. */
  static class MutableClock extends Clock {
    private volatile Instant now = Instant.now();

    void advance(Duration d) {
      now = now.plus(d);
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

  /** Remplace System.exit : conserve les codes demandés. */
  static class ExitRecorder implements ProcessExit {
    final List<Integer> codes = new CopyOnWriteArrayList<>();

    @Override
    public void exit(int code) {
      codes.add(code);
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
  @Primary
  MutableClock testClock() {
    return new MutableClock();
  }

  @Bean
  @Primary
  ExitRecorder exitRecorder() {
    return new ExitRecorder();
  }
}
