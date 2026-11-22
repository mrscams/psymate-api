package com.psymate.agenda.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Durées des tâches de la prise de rendez-vous en ligne (M14-07) : une durée absente, nulle ou
 * négative est refusée au démarrage. Les dépendances ne sont pas touchées par la validation.
 */
class BookingJobsSettingsTest {

  private static final Duration DAY = Duration.ofHours(24);
  private static final Duration MONTH = Duration.ofDays(30);

  private static BookingPurgeJob purge(Duration unverified, Duration closed) {
    return new BookingPurgeJob(null, Clock.systemUTC(), "-", unverified, closed);
  }

  private static BookingExpiryJob expiry(Duration pendingTtl) {
    return new BookingExpiryJob(
        null, null, null, null, null, null, Clock.systemUTC(), "-", pendingTtl);
  }

  @Test
  void purgeRefusesAMissingZeroOrNegativeRetention() {
    for (Duration bad : new Duration[] {null, Duration.ZERO, Duration.ofHours(-1)}) {
      assertThatThrownBy(() -> purge(bad, MONTH))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("app.booking.unverified-retention");
      assertThatThrownBy(() -> purge(DAY, bad))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("app.booking.closed-retention");
    }
    assertThatCode(() -> purge(DAY, MONTH)).doesNotThrowAnyException();
  }

  @Test
  void expiryRefusesAMissingZeroOrNegativePendingDelay() {
    for (Duration bad : new Duration[] {null, Duration.ZERO, Duration.ofHours(-72)}) {
      assertThatThrownBy(() -> expiry(bad))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("app.booking.pending-ttl");
    }
  }
}
