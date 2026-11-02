package com.psymate.identity.audit;

import com.psymate.identity.repository.SecurityEventJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Pas de journal d'audit sans chaîne web : profil cli (M7-08), sans serveur. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AuditConfiguration {

  @Bean
  AuditWriter auditWriter(SecurityEventJpaRepository events) {
    return new AuditWriter(events);
  }
}
