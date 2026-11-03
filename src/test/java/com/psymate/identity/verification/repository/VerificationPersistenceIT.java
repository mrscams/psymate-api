package com.psymate.identity.verification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.identity.model.DocumentAccessLog;
import com.psymate.identity.model.VerificationDecision;
import com.psymate.identity.repository.DocumentAccessLogJpaRepository;
import com.psymate.identity.repository.VerificationDecisionJpaRepository;
import com.psymate.identity.verification.VerificationTestConfig;
import com.psymate.identity.verification.model.VerificationException;
import com.psymate.identity.verification.repository.VerificationRepository.DuePurge;
import com.psymate.identity.verification.service.VerificationService;
import com.psymate.testsupport.TestAdmins;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Persistance JPA du sous-package verification (M8-05, lot 2) : décisions et journal d'accès
 * insérés par les entités append-only, purge idempotente hors transaction, et verrou de la ligne
 * praticien à la soumission.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(VerificationTestConfig.class)
class VerificationPersistenceIT {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private VerificationRepository repo;
  @Autowired private VerificationService service;
  @Autowired private VerificationDecisionJpaRepository decisions;
  @Autowired private DocumentAccessLogJpaRepository accessLogs;

  private UUID tenant;

  @BeforeEach
  void tenant() {
    tenant = UUID.randomUUID();
    jdbc.update("insert into tenant (id, name) values (?, 'Cabinet test')", tenant);
  }

  /** Aucun administrateur actif laissé en base partagée (TestAdmins). */
  @AfterEach
  void closeTestAdmins() {
    TestAdmins.closeInTenant(jdbc, tenant);
  }

  private UUID practitioner(String role, String status) {
    UUID id = UUID.randomUUID();
    String email = id + "@example.test";
    jdbc.update(
        "insert into practitioner (id, tenant_id, email, email_normalized, password_hash,"
            + " first_name, last_name, display_name, profession, role, verification_status)"
            + " values (?, ?, ?, ?, 'x', 'Test', 'Praticien', 'Dr Test', 'coach', ?, ?)",
        id,
        tenant,
        email,
        email,
        role,
        status);
    return id;
  }

  private UUID document(UUID practitioner, String type, Instant purgeAfter, Instant purgedAt) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into verification_document (id, practitioner_id, type, title, storage_key, mime,"
            + " size_bytes, sha256, purge_after, purged_at) values (?, ?, ?, 'Titre', ?,"
            + " 'application/pdf', 10, 'abc', ?, ?)",
        id,
        practitioner,
        type,
        "verification/" + practitioner + "/" + id + ".bin",
        purgeAfter == null ? null : Timestamp.from(purgeAfter),
        purgedAt == null ? null : Timestamp.from(purgedAt));
    return id;
  }

  // ---- (a) tables append-only insérées par l'entité, relues avec leurs types natifs

  @Test
  void decisionAndAccessLogAreInsertedByTheEntitiesAndReadBack() throws Exception {
    UUID admin = practitioner("admin", "approved");
    UUID subject = practitioner("practitioner", "under_review");
    UUID doc = document(subject, "diploma", null, null);
    Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
    UUID decisionId = UUID.randomUUID();
    UUID relatedDecisionId = UUID.randomUUID();
    Map<String, Object> checklist = new LinkedHashMap<>();
    checklist.put("identity", true);
    checklist.put("previousStatus", "under_review");
    checklist.put("notes", List.of("é", "b"));

    decisions.save(
        new VerificationDecision(
            decisionId,
            subject,
            admin,
            "needs_more",
            "note interne",
            "Merci de compléter",
            checklist,
            null,
            at));
    decisions.save(
        new VerificationDecision(
            relatedDecisionId,
            subject,
            admin,
            "appeal_accepted",
            null,
            null,
            Map.of(),
            subject,
            at.plusSeconds(1)));
    UUID withIp = UUID.randomUUID();
    UUID withoutIp = UUID.randomUUID();
    accessLogs.save(
        new DocumentAccessLog(withIp, doc, admin, InetAddress.getByName("2001:db8::7"), at));
    accessLogs.save(new DocumentAccessLog(withoutIp, doc, admin, null, at));

    // SQL brut : indépendant du mapping.
    Map<String, Object> raw =
        jdbc.queryForMap(
            "select checklist->>'previousStatus' as prev, checklist->'notes'->>0 as note0,"
                + " (checklist->>'identity')::boolean as identity, related_id, internal_note,"
                + " message_to_practitioner as msg, decided_at from verification_decision"
                + " where id = ?",
            decisionId);
    assertThat(raw.get("prev")).isEqualTo("under_review");
    assertThat(raw.get("note0")).isEqualTo("é");
    assertThat(raw.get("identity")).isEqualTo(true);
    assertThat(raw.get("related_id")).isNull();
    assertThat(raw.get("internal_note")).isEqualTo("note interne");
    assertThat(raw.get("msg")).isEqualTo("Merci de compléter");
    assertThat(
            jdbc.queryForObject(
                "select checklist = '{}'::jsonb and related_id = ? and message_to_practitioner"
                    + " is null from verification_decision where id = ?",
                Boolean.class,
                subject,
                relatedDecisionId))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select host(ip) from document_access_log where id = ?", String.class, withIp))
        .isEqualTo("2001:db8::7");
    assertThat(
            jdbc.queryForObject(
                "select ip is null from document_access_log where id = ?",
                Boolean.class,
                withoutIp))
        .isTrue();

    // Relecture par les entités.
    VerificationDecision read = decisions.findById(decisionId).orElseThrow();
    assertThat(read.getChecklist())
        .containsEntry("identity", true)
        .containsEntry("previousStatus", "under_review")
        .containsEntry("notes", List.of("é", "b"));
    assertThat(read.getRelatedId()).isNull();
    assertThat(read.getDecidedAt().toInstant()).isEqualTo(at);
    assertThat(accessLogs.findById(withIp).orElseThrow().getIp())
        .isEqualTo(InetAddress.getByName("2001:db8::7"));
    assertThat(accessLogs.findById(withoutIp).orElseThrow().getIp()).isNull();

    // Projection du praticien : seulement les décisions avec message, la plus récente d'abord.
    var messages = repo.decisionMessages(subject);
    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).kind()).isEqualTo("needs_more");
    assertThat(messages.get(0).message()).isEqualTo("Merci de compléter");
    assertThat(messages.get(0).decidedAt()).isEqualTo(at);
    assertThat(decisions.findByPractitionerIdOrderByDecidedAtDescIdAsc(subject))
        .extracting(VerificationDecision::getKind)
        .containsExactly("appeal_accepted", "needs_more");
  }

  @Test
  void anAccessLogWithAnAddressThatIsNotALiteralIsStoredWithoutAddress() {
    UUID admin = practitioner("admin", "approved");
    UUID subject = practitioner("practitioner", "under_review");
    UUID doc = document(subject, "diploma", null, null);

    repo.insertAccessLog(doc, admin, "ab.cd", Instant.now());
    repo.insertAccessLog(doc, admin, "203.0.113.9", Instant.now());

    assertThat(
            jdbc.queryForObject(
                "select count(*) from document_access_log where document_id = ? and ip is null",
                Integer.class,
                doc))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from document_access_log where document_id = ?"
                    + " and host(ip) = '203.0.113.9'",
                Integer.class,
                doc))
        .isEqualTo(1);
  }

  // ---- (b) purge : markPurged idempotent hors transaction, duePurges limite et ordre

  @Test
  void markPurgedOutsideATransactionIsIdempotent() {
    UUID p = practitioner("practitioner", "approved");
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    UUID doc = document(p, "diploma", now.minusSeconds(3600), null);

    repo.markPurged(doc, now);
    Timestamp first =
        jdbc.queryForObject(
            "select purged_at from verification_document where id = ?", Timestamp.class, doc);
    assertThat(first.toInstant()).isEqualTo(now);

    repo.markPurged(doc, now.plusSeconds(60));
    Timestamp second =
        jdbc.queryForObject(
            "select purged_at from verification_document where id = ?", Timestamp.class, doc);
    assertThat(second).isEqualTo(first);
    // La ligne reste, une fois purgée elle n'est plus due.
    assertThat(repo.duePurges(now.plusSeconds(120), 1000))
        .extracting(DuePurge::id)
        .doesNotContain(doc);
  }

  @Test
  void duePurgesHonoursTheLimitTheOrderAndTheDueDate() {
    UUID p = practitioner("practitioner", "rejected");
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    // Dates très anciennes : ces lignes passent avant toutes celles des autres tests.
    Instant base = Instant.parse("2001-01-01T00:00:00Z");
    UUID third = document(p, "diploma", base.plusSeconds(300), null);
    UUID first = document(p, "identity", base.plusSeconds(100), null);
    UUID second = document(p, "other", base.plusSeconds(200), null);
    UUID purged = document(p, "registration", base.plusSeconds(50), base);
    UUID later = document(p, "certification", now.plusSeconds(3600), null);
    UUID undecided = document(p, "professional_card", null, null);
    try {
      List<DuePurge> two = repo.duePurges(now, 2);
      assertThat(two).extracting(DuePurge::id).containsExactly(first, second);

      List<UUID> all = repo.duePurges(now, 100_000).stream().map(DuePurge::id).toList();
      assertThat(all).contains(first, second, third);
      assertThat(all.indexOf(first)).isLessThan(all.indexOf(second));
      assertThat(all.indexOf(second)).isLessThan(all.indexOf(third));
      assertThat(all).doesNotContain(purged, later, undecided);
      assertThat(repo.duePurges(now, 2).get(0).storageKey())
          .isEqualTo("verification/" + p + "/" + first + ".bin");

      // Comparaison stricte : la ligne dont purge_after vaut exactement « maintenant » n'est pas
      // due.
      assertThat(repo.duePurges(base.plusSeconds(100), 100_000))
          .extracting(DuePurge::id)
          .doesNotContain(first, second, third);
    } finally {
      // Aucune ligne échue laissée à la purge d'un autre test (revue A-19, B3).
      jdbc.update(
          "update verification_document set purged_at = now()"
              + " where practitioner_id = ? and purged_at is null and purge_after is not null",
          p);
    }
  }

  // ---- (c) la soumission verrouille la ligne praticien

  @Test
  void twoConcurrentSubmissionsRecordASingleVerificationSubmitted() throws Exception {
    for (int round = 0; round < 3; round++) {
      UUID p = practitioner("practitioner", "docs_pending");
      document(p, "diploma", null, null);
      document(p, "identity", null, null);

      int workers = 2;
      ExecutorService pool = Executors.newFixedThreadPool(workers);
      List<Future<Boolean>> results = new ArrayList<>();
      try {
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < workers; i++) {
          results.add(
              pool.submit(
                  () -> {
                    start.await();
                    try {
                      service.submit(p);
                      return true;
                    } catch (VerificationException e) {
                      return false;
                    }
                  }));
        }
        start.countDown();
        int accepted = 0;
        for (Future<Boolean> f : results) {
          if (f.get(30, TimeUnit.SECONDS)) {
            accepted++;
          }
        }
        // Le second voit under_review une fois le verrou rendu : VERIFICATION_LOCKED.
        assertThat(accepted).isEqualTo(1);
      } finally {
        pool.shutdownNow();
      }

      assertThat(
              jdbc.queryForObject(
                  "select count(*) from security_event where practitioner_id = ?"
                      + " and kind = 'verification_submitted'",
                  Integer.class,
                  p))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "select verification_status from practitioner where id = ?", String.class, p))
          .isEqualTo("under_review");
    }
  }
}
