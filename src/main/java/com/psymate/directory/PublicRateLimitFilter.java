package com.psymate.directory;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limitation de débit par adresse IP sur /api/public/** (G Q24) : un seau Bucket4j par IP. */
class PublicRateLimitFilter extends OncePerRequestFilter {

  private static final int MAX_TRACKED = 10_000;

  private final int limitPerMinute;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  PublicRateLimitFilter(int limitPerMinute) {
    this.limitPerMinute = limitPerMinute;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (buckets.size() > MAX_TRACKED) {
      buckets.values().removeIf(b -> b.getAvailableTokens() >= limitPerMinute);
    }
    Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), ip -> newBucket());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      chain.doFilter(request, response);
      return;
    }
    long retryAfter =
        Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
    response.setStatus(429);
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"code\":\"RATE_LIMITED\","
                + "\"detail\":\"Trop de requêtes. Réessayez dans "
                + retryAfter
                + " s.\",\"retryAfterSeconds\":"
                + retryAfter
                + "}");
  }

  private Bucket newBucket() {
    return Bucket.builder()
        .addLimit(
            l -> l.capacity(limitPerMinute).refillIntervally(limitPerMinute, Duration.ofMinutes(1)))
        .build();
  }
}
