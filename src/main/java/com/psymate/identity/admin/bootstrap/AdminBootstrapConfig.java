package com.psymate.identity.admin.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AdminBootstrapConfig {

  /** Ferme proprement le contexte puis termine la JVM avec le code demandé. */
  @Bean
  @ConditionalOnMissingBean(ProcessExit.class)
  ProcessExit processExit(ConfigurableApplicationContext context) {
    return code -> System.exit(SpringApplication.exit(context, () -> code));
  }
}
