package com.psymate.overview;

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
 * Horloge réglable pour les tests de la vue d'ensemble : les fenêtres, les points de la tendance et
 * l'âge se calent sur « aujourd'hui », qui ne doit pas changer au milieu d'un test (minuit UTC).
 * Les services d'écriture de clinical partagent cette horloge, comme avec {@code
 * ClinicalTestConfig}. Chaque test repart de {@link #NOW}.
 */
@TestConfiguration
class OverviewTestConfig {

  /** Instant courant de départ de tous les tests overview (un jeudi, 12 h UTC). */
  static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

  /** Horloge dont le test choisit l'instant courant. */
  static class MutableClock extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

    void set(Instant instant) {
      now.set(instant);
    }

    void advance(Duration d) {
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
  MutableClock overviewTestClock() {
    return new MutableClock();
  }
}
