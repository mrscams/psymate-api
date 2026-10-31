package com.psymate.directory;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite dédiée au formulaire de contact (G Q24, fiche 09) : {@code limitPerHour} envois par heure
 * et par IP (IPv6 en /64), seau distinct de celui des lectures publiques (60 / min). Toute
 * tentative compte, acceptée ou refusée en silence : c'est une protection anti-spam.
 *
 * <p>La route est reconnue sur le chemin décodé (même matcher que Spring MVC), jamais sur l'URI
 * brute : {@code /%63ontact} compte comme {@code /contact}.
 */
class ContactRateLimitFilter extends OncePerRequestFilter {

  static final String CONTACT_PATTERN = "/api/public/directory/practitioners/*/contact";

  private static final RequestMatcher CONTACT =
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, CONTACT_PATTERN);

  private final IpBuckets buckets;

  ContactRateLimitFilter(int limitPerHour) {
    this.buckets = new IpBuckets(limitPerHour, Duration.ofHours(1));
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !CONTACT.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    ConsumptionProbe probe = buckets.tryConsume(request.getRemoteAddr());
    if (probe.isConsumed()) {
      chain.doFilter(request, response);
      return;
    }
    IpBuckets.writeTooManyRequests(response, probe);
  }
}
