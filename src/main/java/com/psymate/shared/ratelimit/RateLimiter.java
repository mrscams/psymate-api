package com.psymate.shared.ratelimit;

import com.psymate.shared.ratelimit.RateLimitProperties.Key;
import com.psymate.shared.ratelimit.RateLimitProperties.Rule;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Seaux Bucket4j de toutes les règles de {@link RateLimitProperties} (G Q24). Chaque règle a sa
 * propre table, bornée : nettoyage périodique des seaux pleins (un demandeur qui n'a rien consommé
 * ne coûte plus de mémoire) et, au-delà de {@link #MAX_TRACKED} demandeurs suivis, les nouveaux
 * partagent un seau commun (protection de la mémoire contre un scan avec des clés innombrables).
 *
 * <p>Les clés {@code ip} sont normalisées par {@link #ipKey} : une IPv6 est regroupée par préfixe
 * /64, une IPv4 reste telle quelle.
 */
public final class RateLimiter {

  public static final int MAX_TRACKED = 50_000;
  private static final long CLEANUP_EVERY_MILLIS = 60_000;
  private static final String OVERFLOW = "overflow";

  private final Map<String, Table> tables = new LinkedHashMap<>();

  public RateLimiter(RateLimitProperties properties) {
    properties.rules().forEach((name, rule) -> tables.put(name, new Table(rule)));
  }

  /** Règle configurée ; {@link IllegalArgumentException} si le nom est inconnu. */
  public Rule rule(String ruleName) {
    return table(ruleName).rule;
  }

  /**
   * Consomme un jeton de la règle pour ce demandeur ; la sonde indique l'attente avant le suivant.
   */
  public ConsumptionProbe tryConsume(String ruleName, String key) {
    return table(ruleName).tryConsume(key);
  }

  /** Nombre de seaux actuellement suivis pour cette règle (supervision et tests). */
  public int tracked(String ruleName) {
    return table(ruleName).buckets.size();
  }

  private Table table(String ruleName) {
    Table t = tables.get(ruleName);
    if (t == null) {
      throw new IllegalArgumentException("Unknown rate limit rule: " + ruleName);
    }
    return t;
  }

  /** Une IPv6 est regroupée par préfixe /64 ; une IPv4 reste telle quelle. */
  public static String ipKey(String remoteAddr) {
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
      } catch (UnknownHostException e) {
        return remoteAddr;
      }
    }
    return remoteAddr;
  }

  private static final class Table {

    private final Rule rule;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    private Table(Rule rule) {
      this.rule = rule;
    }

    ConsumptionProbe tryConsume(String rawKey) {
      cleanupIfDue();
      String key = rule.key() == Key.IP ? ipKey(rawKey) : String.valueOf(rawKey);
      Bucket bucket = buckets.get(key);
      if (bucket == null) {
        // Au-delà de la borne, les nouvelles clés partagent un seau commun (repris d'IpBuckets) :
        // pour la règle session, 50 000 sessions actives en même temps ramèneraient toutes les
        // nouvelles sessions à un seul quota de 600 / min jusqu'au nettoyage suivant.
        bucket =
            buckets.size() >= MAX_TRACKED
                ? buckets.computeIfAbsent(OVERFLOW, k -> newBucket())
                : buckets.computeIfAbsent(key, k -> newBucket());
      }
      return bucket.tryConsumeAndReturnRemaining(1);
    }

    private void cleanupIfDue() {
      long now = System.currentTimeMillis();
      long last = lastCleanup.get();
      if (now - last >= CLEANUP_EVERY_MILLIS && lastCleanup.compareAndSet(last, now)) {
        buckets.values().removeIf(b -> b.getAvailableTokens() >= rule.limit());
      }
    }

    private Bucket newBucket() {
      return Bucket.builder()
          .addLimit(l -> l.capacity(rule.limit()).refillIntervally(rule.limit(), rule.period()))
          .build();
    }
  }
}
