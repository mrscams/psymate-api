package com.psymate.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base dédiée des tests {@code *AppRoleIT} (B6) : l'application se connecte avec le rôle
 * psymate_app (G Q9), Flyway avec le propriétaire psymate. Le conteneur est neuf et s'arrête avec
 * le contexte ; le test porte {@code @DirtiesContext}, comme pour {@link DedicatedPostgres}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AppRolePostgres {

  @Bean(destroyMethod = "stop", initMethod = "start")
  PostgreSQLContainer postgres() {
    return PostgresContainers.pgvector();
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
