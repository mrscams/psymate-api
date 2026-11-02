package com.psymate.identity.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.identity.auth.AuthTestConfig.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Verrou de connexion en mémoire (5 échecs, fenêtre et verrou de 15 min). */
class LoginAttemptLimiterTest {

  @Test
  void limiterLocksOnFifthFailureForFifteenMinutes() {
    MutableClock clock = new MutableClock();
    LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
    for (int i = 1; i < 5; i++) {
      assertThat(limiter.recordFailure("k").locked(clock.instant())).isFalse();
    }
    assertThat(limiter.recordFailure("k").locked(clock.instant())).isTrue();
    assertThat(limiter.remainingLock("k"))
        .hasValueSatisfying(d -> assertThat(d.toSeconds()).isEqualTo(900));
    clock.advance(Duration.ofMinutes(15));
    assertThat(limiter.remainingLock("k")).isEmpty();
  }

  @Test
  void limiterFailuresOutsideTheWindowDoNotAccumulate() {
    MutableClock clock = new MutableClock();
    LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
    for (int i = 0; i < 4; i++) {
      limiter.recordFailure("a");
    }
    clock.advance(Duration.ofMinutes(16));
    assertThat(limiter.recordFailure("a").failures()).isEqualTo(1);
    assertThat(limiter.recordFailure("b").failures()).isEqualTo(1);
  }
}
