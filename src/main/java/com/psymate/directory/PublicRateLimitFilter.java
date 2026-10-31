package com.psymate.directory;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limitation de débit par adresse IP sur /api/public/** (G Q24) : un seau Bucket4j par IP (IPv6
 * ramenée à son /64, voir {@link IpBuckets}). L'adresse est celle de {@code getRemoteAddr()} :
 * derrière un proxy, le déploiement doit activer {@code server.forward-headers-strategy} (l'en-tête
 * n'est jamais lu ici).
 */
class PublicRateLimitFilter extends OncePerRequestFilter {

  static final int MAX_TRACKED = IpBuckets.MAX_TRACKED;

  private final IpBuckets buckets;

  PublicRateLimitFilter(int limitPerMinute) {
    this.buckets = new IpBuckets(limitPerMinute, Duration.ofMinutes(1));
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

  /** Une IPv6 est regroupée par préfixe /64 ; une IPv4 reste telle quelle. */
  static String key(String remoteAddr) {
    return IpBuckets.key(remoteAddr);
  }

  int trackedBuckets() {
    return buckets.tracked();
  }
}
