package com.psymate.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Un seul Mailpit par JVM, sur le modèle de {@link SharedPostgres} : un champ {@code static} qui
 * n'est pas un bean, pour qu'aucune fermeture de contexte ne l'arrête. Les tests le lisent par
 * {@link #container()} et ne regardent que les messages de leurs propres destinataires ({@link
 * MailpitClient}). Mailpit garde tous les messages ({@code MP_MAX_MESSAGES=0}) : sans cela, il
 * effacerait les plus anciens au-delà de 500, sur toute une tranche.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SharedMailpit {

  private static final GenericContainer<?> CONTAINER =
      new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.32.0"))
          .withExposedPorts(1025, 8025)
          .withEnv("MP_MAX_MESSAGES", "0")
          .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

  /** Le conteneur de la JVM, démarré si besoin. */
  public static synchronized GenericContainer<?> container() {
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }
    return CONTAINER;
  }

  @Bean
  DynamicPropertyRegistrar sharedMailpitProperties() {
    return registry -> {
      registry.add("spring.mail.host", () -> container().getHost());
      registry.add("spring.mail.port", () -> container().getMappedPort(1025));
    };
  }
}
