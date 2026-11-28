package com.psymate.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Compteur à fenêtre glissante (M15-06 : 5 échecs en 15 minutes), horloge injectée. */
class BillingWindowCounterTest {

  private static final Duration WINDOW = Duration.ofMinutes(15);

  private final MovableClock clock = new MovableClock(Instant.parse("2026-11-28T10:00:00Z"));
  private final BillingWindowCounter counter = new BillingWindowCounter(5, WINDOW, clock);
  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();

  @Test
  void fiveEventsInTheWindowBlockTheKey() {
    for (int i = 0; i < 4; i++) {
      counter.record(alice);
      clock.advance(Duration.ofMinutes(1));
    }
    assertThat(counter.blockedFor(alice)).isEmpty();
    counter.record(alice);
    // Le plus ancien échec (10:00) sort de la fenêtre à 10:15 ; il est 10:04.
    assertThat(counter.blockedFor(alice)).contains(Duration.ofMinutes(11));
    assertThat(counter.blockedFor(bob)).as("une autre clé n'est pas touchée").isEmpty();
  }

  @Test
  void leavingTheWindowUnblocks() {
    for (int i = 0; i < 5; i++) {
      counter.record(alice);
    }
    clock.advance(WINDOW.minusSeconds(1));
    assertThat(counter.blockedFor(alice)).contains(Duration.ofSeconds(1));
    clock.advance(Duration.ofSeconds(1));
    assertThat(counter.blockedFor(alice)).isEmpty();
  }

  @Test
  void slidingWindowFreesOneSlotAtATime() {
    counter.record(alice);
    clock.advance(Duration.ofMinutes(10));
    for (int i = 0; i < 4; i++) {
      counter.record(alice);
    }
    assertThat(counter.blockedFor(alice)).contains(Duration.ofMinutes(5));
    clock.advance(Duration.ofMinutes(5));
    assertThat(counter.blockedFor(alice)).isEmpty();
    counter.record(alice);
    assertThat(counter.blockedFor(alice)).contains(Duration.ofMinutes(10));
  }

  @Test
  void resetClearsTheKey() {
    for (int i = 0; i < 5; i++) {
      counter.record(alice);
    }
    assertThat(counter.blockedFor(alice)).isPresent();
    counter.reset(alice);
    assertThat(counter.blockedFor(alice)).isEmpty();
    for (int i = 0; i < 4; i++) {
      counter.record(alice);
    }
    assertThat(counter.blockedFor(alice)).as("le compte repart de zéro").isEmpty();
  }

  @Test
  void expiredKeysAreRemovedSoTheTableStaysBounded() {
    for (int i = 0; i < 100; i++) {
      counter.record(UUID.randomUUID());
    }
    assertThat(counter.tracked()).isEqualTo(100);
    clock.advance(WINDOW);
    counter.record(alice);
    assertThat(counter.tracked()).isEqualTo(1);
    clock.advance(WINDOW);
    assertThat(counter.blockedFor(alice)).isEmpty();
    assertThat(counter.tracked()).isZero();
  }

  @Test
  void limitAndWindowMustBePositive() {
    assertThatThrownBy(() -> new BillingWindowCounter(0, WINDOW, clock))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BillingWindowCounter(5, Duration.ZERO, clock))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** Horloge que le test avance. */
  private static final class MovableClock extends Clock {

    private Instant now;

    MovableClock(Instant start) {
      this.now = start;
    }

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
