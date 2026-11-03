package com.psymate.clinical;

import com.psymate.testsupport.SharedPostgres;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** Conteneur pgvector et horloge pilotable pour les tests du module clinical. */
@TestConfiguration(proxyBeanMethods = false)
@Import(SharedPostgres.class)
public class ClinicalTestConfig {

  /** Horloge dont le test choisit l'instant courant. */
  public static class MutableClock extends Clock {
    private final AtomicReference<Instant> now =
        new AtomicReference<>(Instant.parse("2026-10-30T10:00:00Z"));

    public void set(Instant instant) {
      now.set(instant);
    }

    public void advance(Duration d) {
      now.updateAndGet(i -> i.plus(d));
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
      return now.get();
    }
  }

  @Bean
  @Primary
  MutableClock testClock() {
    return new MutableClock();
  }
}
