package com.psymate.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base dédiée (risques B2, B5, B6 de la revue §5 A-19) : un conteneur neuf pour le contexte, arrêté
 * avec lui. Réservée à la liste fermée de {@code SharedDatabaseHygieneTest}. Le test porte aussi
 * {@code @DirtiesContext}, pour que son contexte, et donc sa base, ne servent à aucune autre classe
 * et soient libérés dès la fin de la classe.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DedicatedPostgres {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return PostgresContainers.pgvector();
  }
}
