package com.psymate.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.psymate.clinical.api.EntryCorrected;
import com.psymate.clinical.api.EntryCreated;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.search.SearchSupport;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Maintenance du search_vector : triggers de V08 (création, nouvelle version, correction en grâce)
 * et abonnés EntryCreated / EntryCorrected (tags et émotion, écrits hors de la version).
 */
class SearchIndexListenerIT extends SearchSupport {

  @Autowired private DomainEventPublisher publisher;

  private static Instant daysAgo(int days) {
    return Instant.now().minus(days, ChronoUnit.DAYS);
  }

  private boolean hasVector(UUID entryId) {
    return count("select count(*) from entry where id = ? and search_vector is not null", entryId)
        == 1;
  }

  private boolean found(String q, UUID entryId) throws Exception {
    return ids(search("q", q)).contains(entryId.toString());
  }

  private int completed(String listenerId, UUID entryId) {
    return count(
        "select count(*) from event_publication where listener_id = ?"
            + " and serialized_event like ? and completion_date is not null",
        listenerId,
        "%" + entryId + "%");
  }

  @Test
  void anEntryCreatedByTheApiHasAVectorAsSoonAsItIsCommitted() throws Exception {
    UUID id = note("Séance sur le deuil et la solitude", daysAgo(2));

    assertThat(hasVector(id)).isTrue();
    assertThat(found("solitude", id)).isTrue();
  }

  @Test
  void theEntryCreatedSubscriberIndexesTagsAndEmotionWrittenAfterTheVersion() throws Exception {
    createTag("zen");
    UUID id = feeling("anxiety", "Une soirée comme les autres", daysAgo(2), "zen");

    // Étiquette et libellé d'émotion (poids B) arrivent par l'abonné, après le commit.
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(found("zen", id)).isTrue();
              assertThat(found("anxiete", id)).isTrue();
            });
    assertThat(completed("search.entry-created", id)).isEqualTo(1);
  }

  @Test
  void theEntryCorrectedSubscriberIndexesATagChangeThatCreatesNoVersion() throws Exception {
    createTag("zen");
    UUID id = note("Une note simple sur le sommeil", daysAgo(2));
    UUID tagId = UUID.fromString(tagIdOf("zen"));
    assertThat(found("zen", id)).isFalse();

    // Correction « tags seulement » : pas de nouvelle version, donc aucun trigger.
    jdbc.update("insert into entry_tag (entry_id, tag_id) values (?, ?)", id, tagId);
    assertThat(ids(search("tags", "zen"))).containsExactly(id.toString());
    assertThat(found("zen", id)).isFalse();

    tx.executeWithoutResult(
        s -> publisher.publish(new EntryCorrected(tenant, id, patient, "note", 1, 1)));

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(found("zen", id)).isTrue());
    assertThat(ids(search("q", "tag:zen"))).containsExactly(id.toString());
    assertThat(completed("search.entry-corrected", id)).isEqualTo(1);
  }

  @Test
  void replayingAnEventIsHarmlessAndAnUnknownEntryIsIgnored() throws Exception {
    UUID id = note("Une note sur la peur du vide", daysAgo(2));
    UUID unknown = UUID.randomUUID();

    tx.executeWithoutResult(
        s -> {
          publisher.publish(new EntryCreated(tenant, id, patient, "note", 1));
          publisher.publish(new EntryCreated(tenant, id, patient, "note", 1));
          publisher.publish(new EntryCreated(tenant, unknown, patient, "note", 1));
          publisher.publish(new EntryCorrected(tenant, unknown, patient, "note", 1, 2));
        });

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(completed("search.entry-created", unknown)).isEqualTo(1);
              assertThat(completed("search.entry-corrected", unknown)).isEqualTo(1);
              assertThat(completed("search.entry-created", id)).isGreaterThanOrEqualTo(2);
            });
    assertThat(found("vide", id)).isTrue();
    assertThat(count("select count(*) from entry where id = ?", unknown)).isZero();
  }

  @Test
  void aGraceCorrectionRewritingTheVersionInPlaceUpdatesTheVector() throws Exception {
    UUID id = note("Il parle de son déménagement", daysAgo(2));
    assertThat(found("déménagement", id)).isTrue();

    send(
            MockMvcRequestBuilders.patch("/api/entries/{id}", id),
            Map.of("body", "Il parle de sa promotion"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

    assertThat(count("select count(*) from entry_version where entry_id = ?", id))
        .as("correction en grâce : aucune nouvelle version")
        .isEqualTo(1);
    assertThat(found("promotion", id)).isTrue();
    assertThat(found("déménagement", id)).isFalse();
  }

  @Test
  void aNewVersionMadeCurrentUpdatesTheVector() throws Exception {
    UUID id = note("Il parle de son déménagement", daysAgo(2));
    UUID version2 = UUID.randomUUID();

    tx.executeWithoutResult(
        s -> {
          jdbc.update(
              "insert into entry_version (id, entry_id, version, body, reason, author_id)"
                  + " values (?, ?, 2, 'Il parle de sa retraite', 'précision', ?)",
              version2,
              id,
              me);
          jdbc.update(
              "update entry set current_version_id = ?, current_version = 2 where id = ?",
              version2,
              id);
        });

    assertThat(found("retraite", id)).isTrue();
    assertThat(found("déménagement", id)).isFalse();
  }

  @Test
  void aVersionThatIsNotCurrentDoesNotChangeTheVector() throws Exception {
    UUID id = note("Il parle de son déménagement", daysAgo(2));

    jdbc.update(
        "insert into entry_version (id, entry_id, version, body, reason, author_id)"
            + " values (?, ?, 2, 'Il parle de sa retraite', 'brouillon', ?)",
        UUID.randomUUID(),
        id,
        me);

    assertThat(found("retraite", id)).isFalse();
    assertThat(found("déménagement", id)).isTrue();
  }

  private String tagIdOf(String label) {
    return jdbc.queryForObject(
        "select id::text from tag where tenant_id = ? and normalized_label = ?",
        String.class,
        tenant,
        label);
  }
}
