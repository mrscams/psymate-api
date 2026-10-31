package com.psymate.identity.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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

  /**
   * Gestionnaire d'authentification qui refuse tout : la connexion est faite par LoginService
   * (verrou, 2FA, statuts). Sa présence empêche Spring Boot de créer l'utilisateur en mémoire par
   * défaut (mot de passe généré).
   */
  @Bean
  AuthenticationManager authenticationManager() {
    return authentication -> {
      throw new ProviderNotFoundException("login handled by LoginService");
    };
  }

  /** Absente en profil cli (sans serveur web, M7-08) : HttpSecurity n'existe qu'en servlet. */
  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SecurityContextRepository repo,
      PractitionerRepository practitioners,
      TotpRepository totp)
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
                        "/api/auth/register",
                        "/api/auth/signup",
                        "/api/auth/email/confirm",
                        "/api/auth/email/resend",
                        "/api/auth/email/change",
                        "/api/auth/login",
                        "/api/auth/password/change",
                        "/api/me/appeal",
                        "/api/auth/logout",
                        "/api/auth/password-reset/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .addFilterAfter(new EmailConfirmationFilter(practitioners, repo), AuthorizationFilter.class)
        .addFilterAfter(new AdminTwoFactorFilter(totp), EmailConfirmationFilter.class)
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
