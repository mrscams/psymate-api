package com.psymate.directory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Chaîne dédiée à /api/public/** : ni session ni CSRF ni authentification, et jamais
 * l'EmailConfirmationFilter d'identity (il vit dans l'autre chaîne). Limitation de débit par IP.
 */
@Configuration(proxyBeanMethods = false)
class PublicSecurityConfig {

  @Bean
  @Order(1)
  SecurityFilterChain publicDirectoryChain(
      HttpSecurity http, @Value("${app.directory.rate-limit-per-minute:60}") int limitPerMinute)
      throws Exception {
    http.securityMatcher("/api/public/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // Cache-Control posé par les contrôleurs (public, 60 s) : on empêche Spring Security de
        // le remplacer par no-cache.
        .headers(h -> h.cacheControl(c -> c.disable()))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .addFilterBefore(new PublicRateLimitFilter(limitPerMinute), AuthorizationFilter.class);
    return http.build();
  }
}
