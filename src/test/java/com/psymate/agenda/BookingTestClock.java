package com.psymate.agenda;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Horloge injectée des IT de l'expiration et de la purge (M14-07) : l'heure réelle, décalée de ce
 * que le test choisit. Avancer l'horloge de 73 h fait voir à l'application une demande vérifiée il
 * y a 73 h, sans attendre ni réécrire la base. Le test remet le décalage à zéro après lui.
 */
@TestConfiguration(proxyBeanMethods = false)
public class BookingTestClock {

  /** Heure réelle plus un décalage réglable. */
  public static class OffsetClock extends Clock {
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

    /** L'application voit maintenant l'heure réelle plus {@code d}. */
    public void advance(Duration d) {
      offset.set(d);
    }

    /** Retour à l'heure réelle. */
    public void reset() {
      offset.set(Duration.ZERO);
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
      return Instant.now().plus(offset.get());
    }
  }

  @Bean
  @Primary
  OffsetClock bookingTestClock() {
    return new OffsetClock();
  }
}
