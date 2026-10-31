package com.psymate.shared.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Pas de journal d'audit sans chaîne web : profil cli (M7-08), sans serveur. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AuditConfiguration {

  @Bean
  AuditWriter auditWriter(JdbcClient jdbc) {
    return new AuditWriter(jdbc);
  }
}
