package com.psymate.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.psymate.App;
import com.psymate.events.api.DomainEvent;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.testsupport.DedicatedPostgres;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M4-01 : une publication dont l'abonné a échoué est rejouée, avec ou sans filtre d'âge. Le seuil
 * de démarrage est mis à zéro ici pour éprouver le chemin ApplicationReadyEvent ; la base de ce
 * contexte est neuve, donc le rejeu au vrai démarrage ne trouve rien.
 */
@SpringBootTest(properties = "app.events.republish-older-than=0s")
@ActiveProfiles("test")
@Import({DedicatedPostgres.class, EventRepublicationIT.ReplayConfig.class})
@DirtiesContext
class EventRepublicationIT {

  static class ReplayEvent extends DomainEvent {
    private String label;

    ReplayEvent() {}

    ReplayEvent(UUID tenantId, String label) {
      super(tenantId);
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  static class ReplayListener {
    private final AtomicBoolean failNext = new AtomicBoolean();
    private final CopyOnWriteArrayList<UUID> received = new CopyOnWriteArrayList<>();

    void failNextInvocation() {
      failNext.set(true);
    }

    long countReceived(UUID id) {
      return received.stream().filter(id::equals).count();
    }

    @ApplicationModuleListener(id = "test.replay")
    void on(ReplayEvent event) {
      received.add(event.getId());
      if (failNext.compareAndSet(true, false)) {
        throw new IllegalStateException("echec simule");
      }
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ReplayConfig {
    @Bean
    ReplayListener replayListener() {
      return new ReplayListener();
    }
  }

  @Autowired private DomainEventPublisher publisher;
  @Autowired private TransactionTemplate tx;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ReplayListener listener;
  @Autowired private EventRepublisher republisher;
  @Autowired private IncompleteEventPublications incomplete;
  @Autowired private MeterRegistry meters;
  @Autowired private ConfigurableApplicationContext context;

  private ReplayEvent publishFailing() {
    listener.failNextInvocation();
    var event = new ReplayEvent(UUID.randomUUID(), "replay-" + UUID.randomUUID());
    tx.executeWithoutResult(s -> publisher.publish(event));
    await().atMost(Duration.ofSeconds(15)).until(() -> listener.countReceived(event.getId()) == 1);
    // L'échec est enregistré : la ligne reste incomplète.
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> assertThat(status(event)).isEqualTo("FAILED"));
    assertThat(isCompleted(event)).isFalse();
    return event;
  }

  private String status(ReplayEvent e) {
    return jdbc.queryForObject(
        "select status from event_publication where listener_id = 'test.replay'"
            + " and serialized_event like ?",
        String.class,
        "%" + e.getLabel() + "%");
  }

  private boolean isCompleted(ReplayEvent e) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select completion_date is not null from event_publication"
                + " where listener_id = 'test.replay' and serialized_event like ?",
            Boolean.class,
            "%" + e.getLabel() + "%"));
  }

  private void awaitCompleted(ReplayEvent e) {
    await().atMost(Duration.ofSeconds(15)).until(() -> isCompleted(e));
  }

  @Test
  void failedPublicationStaysIncompleteAndReplayRespectsAgeFilter() {
    var event = publishFailing();

    // Trop récente pour un filtre d'une heure : rien n'est rejoué.
    assertThat(republisher.replay(Duration.ofHours(1))).isZero();
    assertThat(isCompleted(event)).isFalse();

    // Sans filtre : rejouée, l'abonné reçoit l'événement une seconde fois et la complète.
    assertThat(republisher.replay(Duration.ZERO)).isGreaterThanOrEqualTo(1);
    awaitCompleted(event);
    assertThat(listener.countReceived(event.getId())).isEqualTo(2);
  }

  @Test
  void incompletePublicationIsResubmittedByTheModulithApi() {
    var event = publishFailing();

    incomplete.resubmitIncompletePublicationsOlderThan(Duration.ZERO);

    awaitCompleted(event);
    assertThat(listener.countReceived(event.getId())).isEqualTo(2);
  }

  /** Critère C2 : « un événement non traité survit à un redémarrage et est rejoué ». */
  @Test
  void applicationReadyEventReplaysFailedPublication() {
    var event = publishFailing();

    context.publishEvent(
        new ApplicationReadyEvent(
            new SpringApplication(App.class), new String[0], context, Duration.ZERO));

    awaitCompleted(event);
    assertThat(listener.countReceived(event.getId())).isEqualTo(2);
  }

  @Test
  void replayIncrementsTheMicrometerCounter() {
    var event = publishFailing();
    double before = meters.counter(EventRepublisher.METRIC).count();

    int replayed = republisher.replay(Duration.ZERO);

    assertThat(replayed).isGreaterThanOrEqualTo(1);
    assertThat(meters.counter(EventRepublisher.METRIC).count()).isEqualTo(before + replayed);
    awaitCompleted(event);
  }
}
