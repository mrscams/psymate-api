package com.psymate.events;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.stereotype.Component;

/**
 * Republication des événements non traités au démarrage (M4-01).
 *
 * <p>Un seul mécanisme de rejeu : la propriété officielle {@code
 * spring.modulith.events.republish-outstanding-events-on-restart} reste à {@code false}, car elle
 * rejoue toutes les publications incomplètes sans filtre d'âge, pendant le refresh du contexte.
 * Ici, le rejeu a lieu une fois l'application prête et ne touche que les publications plus
 * anciennes que {@code app.events.republish-older-than}. Les publications en échec (FAILED) sont
 * rejouées comme les autres.
 *
 * <p>Conséquence : un abonné peut être réinvoqué avec le même événement. Tout abonné
 * {@code @ApplicationModuleListener} doit donc être idempotent (voir {@code EventDeduplicator}).
 */
@Component
@EnableConfigurationProperties(EventsProperties.class)
class EventRepublisher {

  static final String METRIC = "psymate.events.republished";

  private static final Logger LOG = LoggerFactory.getLogger(EventRepublisher.class);

  private final EventsProperties properties;
  private final IncompleteEventPublications incomplete;
  private final MeterRegistry meters;

  EventRepublisher(
      EventsProperties properties, IncompleteEventPublications incomplete, MeterRegistry meters) {
    this.properties = properties;
    this.incomplete = incomplete;
    this.meters = meters;
  }

  @EventListener(ApplicationReadyEvent.class)
  void onStartup() {
    if (properties.republishOnStartup()) {
      replay();
    }
  }

  /** Rejoue les publications incomplètes plus vieilles que le seuil configuré. */
  int replay() {
    return replay(properties.republishOlderThan());
  }

  /** Rejoue les publications incomplètes plus vieilles que {@code olderThan} (ZERO : toutes). */
  int replay(Duration olderThan) {
    try {
      Instant threshold = Instant.now().minus(olderThan);
      // Le prédicat est évalué pour chaque publication incomplète : il sert de filtre d'âge et
      // de compteur (le registre de Spring Modulith n'est pas accessible à la compilation).
      // Le registre attrape l'exception de chaque resoumission (publication illisible, abonné
      // disparu) et passe à la suivante : une publication « poison » ne bloque pas les autres.
      var count = new AtomicInteger();
      incomplete.resubmitIncompletePublications(
          publication -> {
            boolean old = publication.getPublicationDate().isBefore(threshold);
            if (old) {
              count.incrementAndGet();
            }
            return old;
          });
      int replayed = count.get();
      if (replayed > 0) {
        LOG.warn(
            "{} incomplete event publication(s) older than {} resubmitted at startup",
            replayed,
            olderThan);
        meters.counter(METRIC).increment(replayed);
      } else {
        LOG.info("no incomplete event publication older than {} to resubmit", olderThan);
      }
      return replayed;
    } catch (RuntimeException e) {
      LOG.error("event republication failed", e);
      return 0;
    }
  }
}
