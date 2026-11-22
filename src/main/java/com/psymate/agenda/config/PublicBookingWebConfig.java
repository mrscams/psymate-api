package com.psymate.agenda.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@code Cache-Control: no-store} sur toute réponse des routes publiques de prise de rendez-vous
 * (K5.1), erreurs comprises : posé avant l'appel du contrôleur, il reste en place quand un handler
 * d'erreur répond (Spring ne vide que le corps). La chaîne publique désactive le Cache-Control
 * automatique de Spring Security ({@code directory.config.PublicSecurityConfig}) ; le 429 du
 * limiteur de débit porte déjà no-store.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class PublicBookingWebConfig implements WebMvcConfigurer {

  static final String PATTERN = "/api/public/booking/**";

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                return true;
              }
            })
        .addPathPatterns(PATTERN);
  }
}
