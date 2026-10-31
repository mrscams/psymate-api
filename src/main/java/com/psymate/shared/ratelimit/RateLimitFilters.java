package com.psymate.shared.ratelimit;

/**
 * Fabrique des filtres de limitation de débit, à poser dans les chaînes Spring Security des
 * modules. Les filtres ne sont volontairement pas des beans : un {@code Filter} déclaré comme bean
 * serait aussi enregistré par Spring Boot dans le conteneur servlet, avant la sécurité.
 */
public final class RateLimitFilters {

  private final RateLimiter limiter;

  RateLimitFilters(RateLimiter limiter) {
    this.limiter = limiter;
  }

  /** Filtre de la règle {@code ruleName} (voir {@code app.rate-limits.rules}). */
  public RateLimitFilter forRule(String ruleName) {
    return new RateLimitFilter(ruleName, limiter);
  }
}
