package com.psymate.directory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(ContactTestConfig.class)
class ContactPurgeIT {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ContactRequestService service;

  private UUID practitioner() {
    UUID tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet purge')", tenant);
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'T', 'P', 'Purge', 'psychologist', 'approved')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  private UUID insert(UUID practitioner, String createdOffset, String purgeOffset) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into contact_request (id, practitioner_id, sender_name, sender_email, message,"
            + " consent_at, ip_hash, created_at, purge_after) values (?, ?, 'N', 'n@example.test',"
            + " 'm', now(), 'h', now() + ?::interval, now() + ?::interval)",
        id,
        practitioner,
        createdOffset,
        purgeOffset);
    return id;
  }

  private List<UUID> remaining(UUID practitioner) {
    return jdbc.queryForList(
        "select id from contact_request where practitioner_id = ?", UUID.class, practitioner);
  }

  @Test
  void purgeDeletesOnlyRowsPastPurgeAfter() {
    UUID p = practitioner();
    UUID old1 = insert(p, "-31 days", "-1 day");
    UUID old2 = insert(p, "-60 days", "-30 days");
    UUID recent = insert(p, "-1 day", "29 days");
    UUID fresh = insert(p, "0 seconds", "30 days");

    int purged = service.purgeDue();

    assertThat(purged).isGreaterThanOrEqualTo(2);
    assertThat(remaining(p)).containsExactlyInAnyOrder(recent, fresh).doesNotContain(old1, old2);
  }

  @Test
  void jobRunDelegatesToThePurge() {
    UUID p = practitioner();
    UUID old = insert(p, "-40 days", "-10 days");
    UUID recent = insert(p, "0 seconds", "30 days");

    new ContactPurgeJob(service).run();

    assertThat(remaining(p)).containsExactly(recent).doesNotContain(old);
  }
}
