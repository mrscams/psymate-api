package com.psymate.directory;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limitation de débit par adresse IP sur /api/public/** (G Q24) : un seau Bucket4j par IP (IPv6
 * ramenée à son /64). L'adresse est celle de {@code getRemoteAddr()} : derrière un proxy, le
 * déploiement doit activer {@code server.forward-headers-strategy} (l'en-tête n'est jamais lu ici).
 * La table est bornée : nettoyage périodique des seaux pleins, et au-delà de la borne dure les
 * nouvelles adresses partagent un seau commun.
 */
class PublicRateLimitFilter extends OncePerRequestFilter {

  static final int MAX_TRACKED = 50_000;
  private static final long CLEANUP_EVERY_MILLIS = 60_000;
  private static final String OVERFLOW = "overflow";

  private final int limitPerMinute;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

  PublicRateLimitFilter(int limitPerMinute) {
    this.limitPerMinute = limitPerMinute;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    cleanupIfDue();
    String key = key(request.getRemoteAddr());
    Bucket bucket = buckets.get(key);
    if (bucket == null) {
      bucket =
          buckets.size() >= MAX_TRACKED
              ? buckets.computeIfAbsent(OVERFLOW, k -> newBucket())
              : buckets.computeIfAbsent(key, k -> newBucket());
    }
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

  /** Une IPv6 est regroupée par préfixe /64 ; une IPv4 reste telle quelle. */
  static String key(String remoteAddr) {
    if (remoteAddr == null) {
      return "unknown";
    }
    if (remoteAddr.indexOf(':') >= 0) {
      try {
        // Adresse littérale : aucune résolution DNS.
        InetAddress a = InetAddress.getByName(remoteAddr);
        if (a instanceof Inet6Address) {
          return HexFormat.of().formatHex(a.getAddress(), 0, 8) + "/64";
        }
        return a.getHostAddress();
      } catch (java.net.UnknownHostException e) {
        return remoteAddr;
      }
    }
    return remoteAddr;
  }

  private void cleanupIfDue() {
    long now = System.currentTimeMillis();
    long last = lastCleanup.get();
    if (now - last >= CLEANUP_EVERY_MILLIS && lastCleanup.compareAndSet(last, now)) {
      buckets.values().removeIf(b -> b.getAvailableTokens() >= limitPerMinute);
    }
  }

  int trackedBuckets() {
    return buckets.size();
  }

  private Bucket newBucket() {
    return Bucket.builder()
        .addLimit(
            l -> l.capacity(limitPerMinute).refillIntervally(limitPerMinute, Duration.ofMinutes(1)))
        .build();
  }
}
