package com.psymate.directory;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite dédiée au formulaire de contact (G Q24, fiche 09) : {@code limitPerHour} envois par heure
 * et par IP (IPv6 en /64), seau distinct de celui des lectures publiques (60 / min). Toute
 * tentative compte, acceptée ou refusée en silence : c'est une protection anti-spam.
 */
class ContactRateLimitFilter extends OncePerRequestFilter {

  private static final Pattern CONTACT_PATH =
      Pattern.compile("^/api/public/directory/practitioners/[^/]+/contact/?$");

  private final IpBuckets buckets;

  ContactRateLimitFilter(int limitPerHour) {
    this.buckets = new IpBuckets(limitPerHour, Duration.ofHours(1));
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equals(request.getMethod())
        || !CONTACT_PATH.matcher(request.getRequestURI()).matches();
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
