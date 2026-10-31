package com.psymate.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.events.api.DomainEvent;
import com.psymate.events.api.EventDeduplicator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EventDeduplicatorTest {

  static class SomeEvent extends DomainEvent {
    SomeEvent() {
      super(UUID.randomUUID());
    }
  }

  static class IntakeLike {}

  @Test
  void listenerIdIsFqcnMethodAndParameterFqcns() {
    assertThat(EventDeduplicator.listenerId(IntakeLike.class, "on", SomeEvent.class))
        .isEqualTo(IntakeLike.class.getName() + ".on(" + SomeEvent.class.getName() + ")");
    assertThat(EventDeduplicator.listenerId(IntakeLike.class, "on", SomeEvent.class, String.class))
        .endsWith(".on(" + SomeEvent.class.getName() + ",java.lang.String)");
    assertThat(EventDeduplicator.listenerId(IntakeLike.class, "on")).endsWith(".on()");
  }

  @Test
  void onceOutsideTransactionFailsBeforeTouchingTheDatabase() {
    var deduplicator = new JdbcEventDeduplicator(new JdbcTemplate());
    var ran = new boolean[1];

    assertThatThrownBy(() -> deduplicator.once(new SomeEvent(), "x", () -> ran[0] = true))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ran[0]).isFalse();
  }
}
