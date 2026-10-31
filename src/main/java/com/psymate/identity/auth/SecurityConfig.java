package com.psymate.identity.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  @Bean
  @ConditionalOnMissingBean(Clock.class)
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Cookie de session HttpOnly, Secure, SameSite=Lax. Défini explicitement : les propriétés
   * server.servlet.session.cookie.* ne s'appliquent pas hors conteneur embarqué (MockMvc). Avec «
   * rester connecté » (G Q6), le cookie reçoit un Max-Age de 30 jours ; sinon c'est un cookie de
   * navigateur, la session serveur expirant après 12 h d'inactivité.
   */
  @Bean
  CookieSerializer cookieSerializer(
      @Value("${app.auth.session-remember-me}") Duration sessionRememberMe) {
    return new RememberMeCookieSerializer(sessionRememberMe);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    // Argon2id, paramètres explicites au niveau du minimum OWASP (m = 19 MiB, t = 2, p = 1) ;
    // nécessite Bouncy Castle.
    return new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  AuthenticationManager authenticationManager(
      PractitionerUserDetailsService users, PasswordEncoder encoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
    provider.setPasswordEncoder(encoder);
    return new ProviderManager(provider);
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository repo)
      throws Exception {
    http
        // CSRF désactivé : l'API n'accepte que du JSON (application/json, jamais un formulaire
        // classique), et le cookie de session est SameSite=Lax, donc non envoyé sur les POST
        // inter-sites. Les endpoints ne modifient jamais l'état en GET.
        .csrf(csrf -> csrf.disable())
        .securityContext(sc -> sc.securityContextRepository(repo))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/api/auth/password-reset/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, e) -> {
                      response.setStatus(HttpStatus.UNAUTHORIZED.value());
                      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                      response
                          .getWriter()
                          .write(
                              "{\"type\":\"about:blank\",\"title\":\"Non authentifié\","
                                  + "\"status\":401,\"code\":\"UNAUTHENTICATED\","
                                  + "\"detail\":\"Authentification requise.\"}");
                    }));
    return http.build();
  }
}
