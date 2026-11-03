package com.psymate.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Un seul conteneur Postgres par JVM, donc par tranche d'IT (M9-19, revue §5 A-19) : tous les
 * contextes Spring qui importent cette configuration pointent vers la même base. On ne la vide pas
 * entre les tests : chaque test crée ses données et ne regarde que celles-là ({@code
 * SharedDatabaseHygieneTest}, {@code SharedDatabaseIsolationIT}).
 *
 * <p>Le conteneur est un champ {@code static}, démarré par le premier contexte et arrêté par Ryuk à
 * la fin de la JVM. Il n'est <b>pas</b> un bean : Spring Boot ferme tout conteneur déclaré comme
 * bean avec son contexte ({@code TestcontainersLifecycleBeanPostProcessor}, y compris par
 * {@code @ImportTestcontainers} ou avec {@code destroyMethod = ""}). Un contexte évincé du cache
 * (32 au plus) ou fermé par {@code @DirtiesContext} arrêterait alors la base de tous les autres. La
 * connexion passe donc par les propriétés {@code spring.datasource.*}, comme pour {@link
 * AppRolePostgres}.
 *
 * <p>Les tests à base dédiée (liste fermée de {@code SharedDatabaseHygieneTest}) importent {@link
 * DedicatedPostgres} ou {@link AppRolePostgres} à la place ; SearchSchemaIT garde le contexte
 * partagé et migre pas à pas un conteneur à lui, le temps d'une méthode.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SharedPostgres {

  /**
   * Une vingtaine de contextes en cache gardent chacun leur pool sur cette base : 200 connexions au
   * plus (100 par défaut, épuisées dans la tranche identity), et des pools qui rendent leurs
   * connexions inactives ({@link #sharedPostgresProperties()}).
   */
  private static final PostgreSQLContainer CONTAINER =
      PostgresContainers.pgvector()
          .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=200");

  /** Le conteneur de la JVM, démarré si besoin. */
  public static synchronized PostgreSQLContainer container() {
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }
    return CONTAINER;
  }

  @Bean
  DynamicPropertyRegistrar sharedPostgresProperties() {
    return registry -> {
      registry.add("spring.datasource.url", () -> container().getJdbcUrl());
      registry.add("spring.datasource.username", () -> container().getUsername());
      registry.add("spring.datasource.password", () -> container().getPassword());
      // Un contexte en cache ne garde qu'une connexion inactive (10 par défaut) ; le contexte qui
      // tourne en ouvre jusqu'à 10, pour les tests de concurrence, et les rend après 10 s.
      registry.add("spring.datasource.hikari.minimum-idle", () -> 1);
      registry.add("spring.datasource.hikari.idle-timeout", () -> 10_000);
    };
  }
}
