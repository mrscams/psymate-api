package com.psymate.shared.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Règles de limitation de débit (G Q24), section {@code app.rate-limits.rules} de application.yml :
 * une règle nommée = un motif de chemin, des méthodes optionnelles, la clé de comptage ({@code ip}
 * ou {@code session}), un nombre de requêtes et la période au bout de laquelle le seau se recharge.
 *
 * <p>Valeurs de référence (Q24, inchangées) : {@code public-directory} 60 / min / IP, {@code
 * public-contact} 5 / h / IP, {@code session} 600 / min / session.
 */
@ConfigurationProperties("app.rate-limits")
public record RateLimitProperties(Map<String, Rule> rules) {

  public RateLimitProperties {
    rules = rules == null ? Map.of() : Map.copyOf(rules);
  }

  /** Ce qui identifie le demandeur. */
  public enum Key {
    /** Adresse IP de la connexion (IPv6 ramenée à son /64). */
    IP,
    /** Identifiant de la session HTTP ; sans session, la requête n'est pas comptée. */
    SESSION
  }

  /**
   * Une règle. {@code methods} vide = toutes les méthodes ; {@code pattern} est un motif de chemin
   * Spring (sur le chemin décodé).
   */
  public record Rule(String pattern, Set<String> methods, Key key, int limit, Duration period) {

    public Rule {
      if (pattern == null || pattern.isBlank()) {
        throw new IllegalArgumentException("rate limit rule: pattern is required");
      }
      if (key == null) {
        throw new IllegalArgumentException("rate limit rule: key is required");
      }
      if (limit < 1) {
        throw new IllegalArgumentException("rate limit rule: limit must be at least 1");
      }
      if (period == null || period.isZero() || period.isNegative()) {
        throw new IllegalArgumentException("rate limit rule: period must be positive");
      }
      methods = methods == null ? Set.of() : Set.copyOf(methods);
    }
  }
}
