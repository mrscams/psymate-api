package com.psymate.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.psymate.PostgresTestConfig;
import com.psymate.events.api.DomainEvent;
import com.psymate.events.api.DomainEventPublisher;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.core.EventSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, EventPublicationIT.DemoConfig.class})
class EventPublicationIT {

  static class TestEvent extends DomainEvent {
    private String label;

    TestEvent() {}

    TestEvent(UUID tenantId, String label) {
      super(tenantId);
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  static class DemoListener {
    private final CopyOnWriteArrayList<UUID> received = new CopyOnWriteArrayList<>();

    boolean hasReceived(UUID id) {
      return received.contains(id);
    }

    @ApplicationModuleListener
    void on(TestEvent event) {
      received.add(event.getId());
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class DemoConfig {
    @Bean
    DemoListener demoListener() {
      return new DemoListener();
    }
  }

  @Autowired private DomainEventPublisher publisher;
  @Autowired private TransactionTemplate tx;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DemoListener listener;
  @Autowired private EventSerializer serializer;

  private long count(String label) {
    return jdbc.queryForObject(
        "select count(*) from event_publication where serialized_event like ?",
        Long.class,
        "%" + label + "%");
  }

  @Test
  void publishedEventIsPersistedAfterCommit() {
    var label = "persist-" + UUID.randomUUID();
    tx.executeWithoutResult(s -> publisher.publish(new TestEvent(UUID.randomUUID(), label)));
    assertThat(count(label)).isEqualTo(1);
  }

  @Test
  void listenerReceivesEventAndPublicationIsCompleted() {
    var label = "complete-" + UUID.randomUUID();
    var event = new TestEvent(UUID.randomUUID(), label);
    tx.executeWithoutResult(s -> publisher.publish(event));

    await().atMost(Duration.ofSeconds(15)).until(() -> listener.hasReceived(event.getId()));
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForObject(
                            "select count(*) from event_publication"
                                + " where serialized_event like ? and completion_date is not null",
                            Long.class,
                            "%" + label + "%"))
                    .isEqualTo(1));
  }

  /** La republication (M4-01) relira serialized_event : les champs sans setter doivent revenir. */
  @Test
  void domainEventSurvivesSerializationRoundTrip() {
    var event = new TestEvent(UUID.randomUUID(), "round-trip");

    var copy = serializer.deserialize(serializer.serialize(event), TestEvent.class);

    assertThat(copy.getId()).isEqualTo(event.getId());
    assertThat(copy.getOccurredAt()).isEqualTo(event.getOccurredAt());
    assertThat(copy.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(copy.getLabel()).isEqualTo("round-trip");
  }
}
