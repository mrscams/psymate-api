package com.psymate.identity.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Verrouillage après 5 échecs sur une clé (e-mail normalisé + IP) pendant 15 minutes (F33).
 * Compteur en mémoire avec expiration : limite connue, il est propre à chaque instance (V1 mono
 * instance). Il couvre aussi les e-mails inconnus, ce qui évite de révéler l'existence d'un compte.
 * Les colonnes failed_logins / locked_until du praticien reflètent l'état pour l'audit.
 */
@Component
class LoginAttemptLimiter {

  static final int MAX_FAILURES = 5;
  static final Duration WINDOW = Duration.ofMinutes(15);
  static final Duration LOCK = Duration.ofMinutes(15);
  private static final int CLEANUP_THRESHOLD = 10_000;

  /** État d'une clé : échecs dans la fenêtre courante, fin de verrou éventuelle. */
  record State(int failures, Instant windowStart, Instant lockedUntil) {
    boolean locked(Instant now) {
      return lockedUntil != null && now.isBefore(lockedUntil);
    }
  }

  private final Map<String, State> states = new ConcurrentHashMap<>();
  private final Clock clock;

  LoginAttemptLimiter(Clock clock) {
    this.clock = clock;
  }

  /** Durée restante du verrou si la clé est verrouillée. */
  Optional<Duration> remainingLock(String key) {
    Instant now = clock.instant();
    State s = states.get(key);
    if (s == null) {
      return Optional.empty();
    }
    if (s.locked(now)) {
      return Optional.of(Duration.between(now, s.lockedUntil()));
    }
    if (s.lockedUntil() != null) {
      states.remove(key, s);
    }
    return Optional.empty();
  }

  /** Enregistre un échec et retourne l'état résultant. */
  State recordFailure(String key) {
    Instant now = clock.instant();
    if (states.size() > CLEANUP_THRESHOLD) {
      states.values().removeIf(s -> expired(s, now));
    }
    return states.compute(
        key,
        (k, old) -> {
          State base = (old == null || expired(old, now)) ? new State(0, now, null) : old;
          int failures = base.failures() + 1;
          Instant lockedUntil = failures >= MAX_FAILURES ? now.plus(LOCK) : null;
          return new State(failures, base.windowStart(), lockedUntil);
        });
  }

  void reset(String key) {
    states.remove(key);
  }

  private static boolean expired(State s, Instant now) {
    if (s.lockedUntil() != null) {
      return !now.isBefore(s.lockedUntil());
    }
    return !now.isBefore(s.windowStart().plus(WINDOW));
  }
}
