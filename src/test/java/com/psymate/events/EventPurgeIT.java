package com.psymate.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.PostgresTestConfig;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class EventPurgeIT {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private EventPublicationPurgeJob purgeJob;

  private UUID insert(OffsetDateTime published, OffsetDateTime completed, String status) {
    var id = UUID.randomUUID();
    jdbc.update(
        "insert into event_publication (id, listener_id, event_type, serialized_event,"
            + " publication_date, completion_date, status) values (?, ?, ?, ?, ?, ?, ?)",
        id,
        "test.purge",
        "com.psymate.events.PurgeEvent",
        "{\"id\":\"" + id + "\"}",
        published,
        completed,
        status);
    return id;
  }

  private boolean exists(UUID id) {
    return jdbc.queryForObject(
            "select count(*) from event_publication where id = ?", Long.class, id)
        == 1;
  }

  @Test
  void purgesOnlyCompletedPublicationsOlderThanRetention() {
    var now = OffsetDateTime.now(ZoneOffset.UTC);
    var oldCompleted = insert(now.minusDays(32), now.minusDays(31), "COMPLETED");
    var oldIncomplete = insert(now.minusDays(31), null, "PUBLISHED");
    var recentCompleted = insert(now.minusDays(2), now.minusDays(1), "COMPLETED");

    purgeJob.run();

    assertThat(exists(oldCompleted)).isFalse();
    assertThat(exists(oldIncomplete)).isTrue();
    assertThat(exists(recentCompleted)).isTrue();
  }
}
