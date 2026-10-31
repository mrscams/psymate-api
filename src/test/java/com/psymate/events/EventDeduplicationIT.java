package com.psymate.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.psymate.PostgresTestConfig;
import com.psymate.events.api.DomainEvent;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.events.api.EventDeduplicator;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, EventDeduplicationIT.DedupConfig.class})
class EventDeduplicationIT {

  static class DedupEvent extends DomainEvent {
    private String label;

    DedupEvent() {}

    DedupEvent(UUID tenantId, String label) {
      super(tenantId);
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  static class DedupListener {
    private final AtomicInteger effects = new AtomicInteger();
    private final EventDeduplicator deduplicator;

    int effectCount() {
      return effects.get();
    }

    DedupListener(EventDeduplicator deduplicator) {
      this.deduplicator = deduplicator;
    }

    @ApplicationModuleListener(id = "test.dedup")
    void on(DedupEvent event) {
      deduplicator.once(event, "test.dedup", effects::incrementAndGet);
    }
  }

  /** Abonné sans attribut id : Spring calcule lui-même le listener_id. */
  static class NoIdListener {
    private final CopyOnWriteArrayList<UUID> received = new CopyOnWriteArrayList<>();

    boolean hasReceived(UUID id) {
      return received.contains(id);
    }

    @ApplicationModuleListener
    void on(DedupEvent event) {
      received.add(event.getId());
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class DedupConfig {
    @Bean
    DedupListener dedupListener(EventDeduplicator deduplicator) {
      return new DedupListener(deduplicator);
    }

    @Bean
    NoIdListener noIdListener() {
      return new NoIdListener();
    }
  }

  @Autowired private DomainEventPublisher publisher;
  @Autowired private TransactionTemplate tx;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DedupListener dedupListener;
  @Autowired private NoIdListener noIdListener;
  @Autowired private EventDeduplicator deduplicator;

  private long completed(String listenerId, String label) {
    return jdbc.queryForObject(
        "select count(*) from event_publication where listener_id = ?"
            + " and completion_date is not null and serialized_event like ?",
        Long.class,
        listenerId,
        "%" + label + "%");
  }

  @Test
  void sameEventPublishedTwiceRunsTheEffectOnce() {
    var label = "dedup-" + UUID.randomUUID();
    var event = new DedupEvent(UUID.randomUUID(), label);

    tx.executeWithoutResult(s -> publisher.publish(event));
    await().atMost(Duration.ofSeconds(15)).until(() -> completed("test.dedup", label) == 1);
    tx.executeWithoutResult(s -> publisher.publish(event));

    await().atMost(Duration.ofSeconds(15)).until(() -> completed("test.dedup", label) == 2);
    assertThat(dedupListener.effectCount()).isEqualTo(1);
  }

  @Test
  void onceOutsideTransactionFails() {
    var event = new DedupEvent(UUID.randomUUID(), "x");

    assertThatThrownBy(() -> deduplicator.once(event, "test.dedup", () -> {}))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onceInsideTransactionRunsTheEffect() {
    var event = new DedupEvent(UUID.randomUUID(), "direct-" + UUID.randomUUID());
    var runs = new AtomicInteger();

    boolean ran =
        Boolean.TRUE.equals(
            tx.execute(s -> deduplicator.once(event, "test.direct", runs::incrementAndGet)));

    assertThat(ran).isTrue();
    assertThat(runs.get()).isEqualTo(1);
  }

  @Test
  void listenerIdHelperMatchesTheIdSpringStores() {
    var label = "noid-" + UUID.randomUUID();
    var event = new DedupEvent(UUID.randomUUID(), label);
    tx.executeWithoutResult(s -> publisher.publish(event));
    await().atMost(Duration.ofSeconds(15)).until(() -> noIdListener.hasReceived(event.getId()));

    var expected = EventDeduplicator.listenerId(NoIdListener.class, "on", DedupEvent.class);

    var stored =
        jdbc.queryForList(
            "select listener_id from event_publication where serialized_event like ?"
                + " and listener_id <> 'test.dedup'",
            String.class,
            "%" + label + "%");
    assertThat(stored).containsExactly(expected);
  }
}
