package com.psymate.identity.auth;

import com.psymate.testsupport.SharedMailpit;
import com.psymate.testsupport.SharedPostgres;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** Postgres (pgvector) + Mailpit + horloge contrôlée pour AuthIT. */
@TestConfiguration(proxyBeanMethods = false)
@Import({SharedPostgres.class, SharedMailpit.class})
public class AuthTestConfig {

  /** Horloge modifiable, partagée par l'application et le test. */
  public static class MutableClock extends Clock {
    private volatile Instant now = Instant.now();

    public void advance(Duration d) {
      now = now.plus(d);
    }

    public void reset() {
      now = Instant.now();
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

  @Bean
  @Primary
  MutableClock testClock() {
    return new MutableClock();
  }
}
