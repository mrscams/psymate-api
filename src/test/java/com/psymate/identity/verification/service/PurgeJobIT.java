package com.psymate.identity.verification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.identity.verification.VerificationTestConfig;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** R12 : la purge supprime le fichier et garde la ligne (purge_after posé par M1-14). */
@SpringBootTest
@ActiveProfiles("test")
@Import(VerificationTestConfig.class)
class PurgeJobIT {

  @Autowired private PurgeJob job;
  @Autowired private StorageService storage;
  @Autowired private DocumentCipher cipher;
  @Autowired private JdbcTemplate jdbc;

  private UUID practitioner() {
    UUID tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'coach', 'approved')",
        id,
        tenant,
        email,
        email);
    return id;
  }

  private UUID document(UUID practitioner, Instant purgeAfter) {
    UUID id = UUID.randomUUID();
    String key = "verification/" + practitioner + "/" + id + ".bin";
    storage.put(key, cipher.encrypt("%PDF-1.4 contenu".getBytes(StandardCharsets.US_ASCII), key));
    jdbc.update(
        "insert into verification_document (id, practitioner_id, type, title, storage_key, mime,"
            + " size_bytes, sha256, purge_after) values (?, ?, 'diploma', 'Master', ?,"
            + " 'application/pdf', 16, 'abc', ?)",
        id,
        practitioner,
        key,
        purgeAfter == null ? null : Timestamp.from(purgeAfter));
    return id;
  }

  private String key(UUID doc) {
    return jdbc.queryForObject(
        "select storage_key from verification_document where id = ?", String.class, doc);
  }

  @Test
  void purgeRemovesTheFileAndKeepsTheRow() {
    UUID p = practitioner();
    UUID due = document(p, Instant.now().minus(Duration.ofDays(1)));
    UUID later = document(p, Instant.now().plus(Duration.ofDays(30)));
    UUID undecided = document(p, null);
    String dueKey = key(due);

    job.run();

    assertThat(storage.exists(dueKey)).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select purged_at is not null from verification_document where id = ?",
                Boolean.class,
                due))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select title from verification_document where id = ?", String.class, due))
        .isEqualTo("Master");
    for (UUID kept : new UUID[] {later, undecided}) {
      assertThat(storage.exists(key(kept))).isTrue();
      assertThat(
              jdbc.queryForObject(
                  "select purged_at is null from verification_document where id = ?",
                  Boolean.class,
                  kept))
          .isTrue();
    }

    // Idempotent : une seconde exécution ne change rien.
    Timestamp first =
        jdbc.queryForObject(
            "select purged_at from verification_document where id = ?", Timestamp.class, due);
    job.run();
    assertThat(
            jdbc.queryForObject(
                "select purged_at from verification_document where id = ?", Timestamp.class, due))
        .isEqualTo(first);
  }

  @Test
  void aFailingRowDoesNotBlockTheNextOnes() {
    UUID p = practitioner();
    UUID bad = document(p, Instant.now().minus(Duration.ofDays(3)));
    jdbc.update(
        "update verification_document set storage_key = '../../escape.bin' where id = ?", bad);
    UUID good = document(p, Instant.now().minus(Duration.ofDays(1)));
    String goodKey = key(good);

    job.run();

    assertThat(storage.exists(goodKey)).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select purged_at is not null from verification_document where id = ?",
                Boolean.class,
                good))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select purged_at is null from verification_document where id = ?",
                Boolean.class,
                bad))
        .isTrue();
    jdbc.update("update verification_document set purge_after = null where id = ?", bad);
  }

  @Test
  void purgeToleratesAFileAlreadyMissing() {
    UUID p = practitioner();
    UUID due = document(p, Instant.now().minus(Duration.ofHours(1)));
    storage.delete(key(due));

    job.run();

    assertThat(
            jdbc.queryForObject(
                "select purged_at is not null from verification_document where id = ?",
                Boolean.class,
                due))
        .isTrue();
  }
}
