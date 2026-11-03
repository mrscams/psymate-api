package com.psymate.testsupport;

import java.nio.file.Path;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Seul endroit où se construit un conteneur Postgres de test (M9-19, garde de {@code
 * SharedDatabaseHygieneTest}) : même image, même base, même propriétaire et même script d'init que
 * docker-compose (G Q9), donc le rôle psymate_app existe comme en local.
 */
public final class PostgresContainers {

  public static final String IMAGE = "pgvector/pgvector:pg16";

  private PostgresContainers() {}

  /** Un conteneur neuf, non démarré. */
  public static PostgreSQLContainer pgvector() {
    return new PostgreSQLContainer(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
        .withDatabaseName("psymate")
        .withUsername("psymate")
        .withPassword("psymate")
        .withCopyFileToContainer(
            MountableFile.forHostPath(Path.of("docker/postgres/init/01-app-role.sql")),
            "/docker-entrypoint-initdb.d/01-app-role.sql");
  }
}
