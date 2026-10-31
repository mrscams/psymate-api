package com.psymate.shared.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfiguration {

  @Bean
  RateLimiter rateLimiter(RateLimitProperties properties) {
    return new RateLimiter(properties);
  }

  /** Pas de chaîne web en profil cli (commande ponctuelle sans serveur, M7-08). */
  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  RateLimitFilters rateLimitFilters(RateLimiter limiter) {
    return new RateLimitFilters(limiter);
  }
}
