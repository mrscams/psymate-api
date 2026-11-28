package com.psymate.billing.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compteur d'événements par clé (un praticien) sur une fenêtre glissante, en mémoire : au plus
 * {@code limit} événements dans les {@code window} dernières. Sert au plafond d'essais du mot de
 * passe de l'IBAN (M15-06 : 5 échecs en 15 minutes) et servira au plafond d'envois d'e-mails
 * (M15-11 : 30 par heure, G Q94). L'appelant demande {@link #blockedFor} avant d'agir, puis inscrit
 * l'événement ({@link #record}) ; {@link #reset} efface la clé (succès du mot de passe).
 *
 * <p>L'heure vient de l'horloge injectée. La table est bornée : les clés dont tous les événements
 * sont sortis de la fenêtre sont retirées au plus tard une fois par fenêtre (nettoyage paresseux à
 * l'inscription), si bien qu'une clé ne coûte de la mémoire que tant qu'elle a un événement récent.
 * Compteur propre à une instance de l'API (pas partagé entre instances) ; la vérification puis
 * l'inscription ne sont pas atomiques : des demandes simultanées d'un même praticien peuvent
 * dépasser la limite de quelques unités, ce qui suffit à empêcher un essai en masse. Ne journalise
 * rien.
 */
public final class BillingWindowCounter {

  private final int limit;
  private final Duration window;
  private final Clock clock;
  private final ConcurrentHashMap<UUID, Deque<Instant>> events = new ConcurrentHashMap<>();
  private final AtomicLong lastCleanup;

  public BillingWindowCounter(int limit, Duration window, Clock clock) {
    if (limit < 1 || window.isNegative() || window.isZero()) {
      throw new IllegalArgumentException("limite et fenêtre strictement positives");
    }
    this.limit = limit;
    this.window = Objects.requireNonNull(window);
    this.clock = Objects.requireNonNull(clock);
    this.lastCleanup = new AtomicLong(clock.millis());
  }

  /**
   * Attente avant que la clé puisse agir de nouveau, si elle a déjà {@code limit} événements dans
   * la fenêtre (sortie du plus ancien) ; vide si elle peut agir.
   */
  public Optional<Duration> blockedFor(UUID key) {
    Instant now = clock.instant();
    Deque<Instant> recent =
        events.computeIfPresent(key, (k, deque) -> prune(deque, now) ? null : deque);
    if (recent == null) {
      return Optional.empty();
    }
    synchronized (recent) {
      if (recent.size() < limit) {
        return Optional.empty();
      }
      Duration wait = Duration.between(now, recent.peekFirst().plus(window));
      return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
    }
  }

  /** Inscrit un événement de la clé, maintenant. */
  public void record(UUID key) {
    Instant now = clock.instant();
    cleanupIfDue(now);
    events.compute(
        key,
        (k, deque) -> {
          Deque<Instant> d = deque == null ? new ArrayDeque<>() : deque;
          prune(d, now);
          d.addLast(now);
          return d;
        });
  }

  /** Efface les événements de la clé. */
  public void reset(UUID key) {
    events.remove(key);
  }

  /** Nombre de clés suivies (tests et supervision). */
  int tracked() {
    return events.size();
  }

  /** Retire les événements sortis de la fenêtre ; vrai si la file est vide. */
  private boolean prune(Deque<Instant> deque, Instant now) {
    Instant oldestKept = now.minus(window);
    synchronized (deque) {
      while (!deque.isEmpty() && !deque.peekFirst().isAfter(oldestKept)) {
        deque.pollFirst();
      }
      return deque.isEmpty();
    }
  }

  /** Une fois par fenêtre au plus : retire les clés sans événement récent. */
  private void cleanupIfDue(Instant now) {
    long last = lastCleanup.get();
    if (now.toEpochMilli() - last >= window.toMillis()
        && lastCleanup.compareAndSet(last, now.toEpochMilli())) {
      events
          .keySet()
          .forEach(k -> events.computeIfPresent(k, (key, d) -> prune(d, now) ? null : d));
    }
  }
}
