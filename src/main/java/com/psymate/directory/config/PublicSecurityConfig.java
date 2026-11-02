package com.psymate.directory.config;

import com.psymate.shared.ratelimit.RateLimitFilters;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
// Pas de chaîne web en profil cli (commande ponctuelle sans serveur, M7-08).
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class PublicSecurityConfig {

  @Bean
  @Order(1)
  SecurityFilterChain publicDirectoryChain(HttpSecurity http, RateLimitFilters rateLimits)
      throws Exception {
    http.securityMatcher("/api/public/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // Cache-Control posé par les contrôleurs (public, 60 s) : on empêche Spring Security de
        // le remplacer par no-cache.
        .headers(h -> h.cacheControl(c -> c.disable()))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        // Règles de shared (app.rate-limits.rules) : 60 / min / IP sur toute la chaîne ...
        .addFilterBefore(rateLimits.forRule("public-directory"), AuthorizationFilter.class)
        // ... puis, après elle, 5 / h / IP qui ne concerne que POST …/contact. Même position
        // d'ancrage : à ordre égal, Spring Security garde l'ordre d'ajout.
        .addFilterBefore(rateLimits.forRule("public-contact"), AuthorizationFilter.class);
    return http.build();
  }
}
