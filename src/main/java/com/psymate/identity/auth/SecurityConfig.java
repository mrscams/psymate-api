package com.psymate.identity.auth;

import com.psymate.identity.audit.AuditFilter;
import com.psymate.identity.audit.AuditWriter;
import com.psymate.shared.problem.ErrorCode;
import com.psymate.shared.problem.ProblemWriter;
import com.psymate.shared.problem.Problems;
import com.psymate.shared.ratelimit.RateLimitFilter;
import com.psymate.shared.ratelimit.RateLimitFilters;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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
      TotpRepository totp,
      RateLimitFilters rateLimits,
      AuditWriter audit)
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
                        "/api/me/export",
                        "/api/me/export/**",
                        "/api/auth/logout",
                        "/api/auth/password-reset/**")
                    .permitAll()
                    // Rôle admin exigé sur le chemin décodé (les matchers de Spring Security
                    // décodent) : première garde, doublée par AdminTwoFactorFilter pour la 2FA.
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .addFilterAfter(new EmailConfirmationFilter(practitioners, repo), AuthorizationFilter.class)
        .addFilterAfter(new AdminTwoFactorFilter(totp), EmailConfirmationFilter.class)
        // Après l'authentification et les garde-fous du compte : un 401, un 403 ou un compte
        // refusé ne consomme pas de jeton, et la session est déjà chargée (règle « session »,
        // 600 / min, G Q24). Les requêtes sans session ne sont pas comptées ici.
        .addFilterAfter(rateLimits.forRule("session"), AdminTwoFactorFilter.class)
        // Journal d'audit des accès patient (G Q29), après la limite : un 429 n'a rien lu.
        .addFilterAfter(new AuditFilter(audit), RateLimitFilter.class)
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, e) ->
                            ProblemWriter.write(
                                response,
                                Problems.of(
                                    HttpStatus.UNAUTHORIZED,
                                    ErrorCode.UNAUTHENTICATED,
                                    "Non authentifié",
                                    "Authentification requise.")))
                    // Seule règle d'autorisation au-delà d'authenticated() : /api/admin/**.
                    .accessDeniedHandler(
                        (request, response, e) ->
                            ProblemWriter.write(
                                response,
                                Problems.of(
                                    HttpStatus.FORBIDDEN,
                                    ErrorCode.ADMIN_ROLE_REQUIRED,
                                    "Accès réservé à l'administration",
                                    "Cette ressource est réservée aux administrateurs."))));
    return http.build();
  }
}
