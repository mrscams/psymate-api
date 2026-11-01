package com.psymate.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.psymate.clinical.api.EntryArchived;
import com.psymate.clinical.api.EntryCorrected;
import com.psymate.clinical.api.EntryCreated;
import com.psymate.events.api.DomainEvent;
import com.psymate.events.api.DomainEventPublisher;
import com.psymate.linking.api.LinkAccepted;
import com.psymate.patients.api.PatientUpdated;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Abonnés d'événements de la vue d'ensemble : rafraîchissement après chaque événement du dossier,
 * idempotence (deux livraisons du même événement donnent une seule ligne et le même contenu) et
 * refus d'écraser une ligne plus récente.
 */
class OverviewListenerIT extends OverviewSupport {

  private static final Duration WAIT = Duration.ofSeconds(20);

  @Autowired private DomainEventPublisher publisher;
  @Autowired private OverviewRepository repository;

  private int completed(String listenerId, UUID patientId) {
    return count(
        "select count(*) from event_publication where listener_id = ?"
            + " and serialized_event like ? and completion_date is not null",
        listenerId,
        "%" + patientId + "%");
  }

  private void publish(DomainEvent... events) {
    tx.executeWithoutResult(
        s -> {
          for (DomainEvent e : events) {
            publisher.publish(e);
          }
        });
  }

  private void deleteRow() {
    jdbc.update("delete from patient_overview where patient_id = ?", patient);
  }

  @Test
  void anEntryCreatedByTheApiRefreshesTheProjectionWithoutAnyRead() throws Exception {
    feeling("anxiety", 7, daysAgo(1));

    await()
        .atMost(WAIT)
        .untilAsserted(
            () -> {
              String stored = storedPayload(patient);
              assertThat(stored).isNotNull();
              assertThat((String) JsonPath.read(stored, "$.windows['30d'].emotions.top[0].code"))
                  .isEqualTo("anxiety");
              assertThat(
                      jdbc.queryForObject(
                          "select source_rev from patient_overview where patient_id = ?",
                          Long.class,
                          patient))
                  .isEqualTo(clinicalRev());
            });
    assertThat(completed("overview.entry-created", patient)).isGreaterThanOrEqualTo(1);
  }

  @Test
  void eachSubscribedEventRecomputesTheProjection() throws Exception {
    note("Une note", daysAgo(1));
    awaitOverviewQuiet();
    UUID someEntry = UUID.randomUUID();

    record Case(String listenerId, Supplier<DomainEvent> event) {}
    Case[] cases = {
      new Case(
          "overview.entry-created", () -> new EntryCreated(tenant, someEntry, patient, "note", 1)),
      new Case(
          "overview.entry-corrected",
          () -> new EntryCorrected(tenant, someEntry, patient, "note", 1, 2)),
      new Case(
          "overview.entry-archived",
          () -> new EntryArchived(tenant, someEntry, patient, "note", 2)),
      new Case(
          "overview.link-accepted",
          () ->
              new LinkAccepted(
                  tenant,
                  UUID.randomUUID(),
                  patient,
                  UUID.randomUUID(),
                  UUID.randomUUID(),
                  "co_occurs",
                  "manual",
                  null)),
      new Case("overview.patient-updated", () -> new PatientUpdated(tenant, patient, me))
    };

    for (Case c : cases) {
      deleteRow();
      DomainEvent event = c.event().get();
      int before = completed(c.listenerId(), patient);

      publish(event);

      await()
          .atMost(WAIT)
          .untilAsserted(
              () -> {
                assertThat(storedPayload(patient)).isNotNull();
                assertThat(completed(c.listenerId(), patient)).isGreaterThan(before);
              });
      awaitOverviewQuiet();
    }
  }

  @Test
  void theSameEventDeliveredTwiceLeavesOneRowWithTheSamePayload() throws Exception {
    UUID id = feeling("sadness", 6, daysAgo(2));
    awaitOverviewQuiet();
    deleteRow();
    EntryCreated event = new EntryCreated(tenant, id, patient, "feeling", 1);

    deliver(event);
    deliver(event);
    String first = storedPayload(patient);
    assertThat(first).isNotNull();

    deliver(event);
    deliver(event);

    assertThat(count("select count(*) from patient_overview where patient_id = ?", patient))
        .isEqualTo(1);
    assertThat(storedPayload(patient)).isEqualTo(first);
    assertThat((Integer) JsonPath.read(first, "$.windows['30d'].emotions.top[0].count"))
        .isEqualTo(1);
  }

  /**
   * Publie l'événement et attend que l'abonné entry-created l'ait traité, puis seulement rend la
   * main : une livraison après l'autre, comme un rejeu (EventRepublisher). Jamais deux copies du
   * même événement en vol en même temps : le registre de Spring Modulith suit les publications en
   * cours dans une table indexée par (événement, abonné), deux copies identiques y partagent la
   * même clé, et deux complétions quasi simultanées marquent alors la même ligne de {@code
   * event_publication}, l'autre restant incomplète (course observée sur la CI à 2 CPU).
   */
  private void deliver(EntryCreated event) {
    int before = completed("overview.entry-created", patient);
    publish(event);
    await()
        .atMost(WAIT)
        .untilAsserted(
            () -> assertThat(completed("overview.entry-created", patient)).isEqualTo(before + 1));
  }

  @Test
  void aPatientUpdatedEventRefreshesTheFlagsOfTheProjection() throws Exception {
    note("Une note", daysAgo(1));
    awaitOverviewQuiet();
    jdbc.update(
        "insert into patient_flag (id, patient_id, kind, reason, set_by)"
            + " values (?, ?, 'risk', 'Risque évalué', ?)",
        UUID.randomUUID(),
        patient,
        me);

    publish(new PatientUpdated(tenant, patient, me));

    await()
        .atMost(WAIT)
        .untilAsserted(
            () ->
                assertThat((String) JsonPath.read(storedPayload(patient), "$.facts.flags[0].kind"))
                    .isEqualTo("risk"));
  }

  @Test
  void anEventForAnUnknownPatientIsIgnoredWithoutError() {
    UUID ghost = UUID.randomUUID();

    publish(new EntryCreated(tenant, UUID.randomUUID(), ghost, "note", 1));

    await()
        .atMost(WAIT)
        .untilAsserted(() -> assertThat(completed("overview.entry-created", ghost)).isEqualTo(1));
    assertThat(count("select count(*) from patient_overview where patient_id = ?", ghost)).isZero();
  }

  @Test
  void upsertNeverOverwritesARowComputedAtANewerRevision() {
    Instant now = NOW;

    assertThat(repository.upsert(patient, "{\"v\":\"new\"}", 10, now, 5)).isEqualTo(1);
    assertThat(repository.upsert(patient, "{\"v\":\"old\"}", 9, now, 5)).isZero();

    assertThat(storedPayload(patient)).contains("new");
    assertThat(
            jdbc.queryForObject(
                "select source_rev from patient_overview where patient_id = ?",
                Long.class,
                patient))
        .isEqualTo(10L);

    // Même révision : le recalcul remplace la ligne (idempotent), une révision plus haute aussi.
    assertThat(repository.upsert(patient, "{\"v\":\"same\"}", 10, now, 6)).isEqualTo(1);
    assertThat(repository.upsert(patient, "{\"v\":\"newer\"}", 11, now, 7)).isEqualTo(1);
    assertThat(storedPayload(patient)).contains("newer");
    assertThat(count("select count(*) from patient_overview where patient_id = ?", patient))
        .isEqualTo(1);
  }
}
