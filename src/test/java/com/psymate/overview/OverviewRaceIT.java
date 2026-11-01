package com.psymate.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * La projection n'est jamais obsolète (A6, A7 séquence 3) : une lecture faite juste après une
 * écriture, avant que l'abonné asynchrone ait tourné, reflète déjà l'écriture, et sourceRev vaut
 * patient.clinical_rev.
 */
class OverviewRaceIT extends OverviewSupport {

  @Test
  void aFeelingJustCreatedIsInTheOverviewImmediately() throws Exception {
    for (int i = 1; i <= 15; i++) {
      feeling("anxiety", 5, daysAgo(1));

      DocumentContext doc = overview("window", "30d");

      assertThat(doc.read("$.emotions.top[0].code", String.class)).isEqualTo("anxiety");
      assertThat(doc.read("$.emotions.top[0].count", Integer.class))
          .as("passage " + i)
          .isEqualTo(i);
      assertThat(doc.read("$.sourceRev", Long.class)).isEqualTo(clinicalRev());
    }
  }

  @Test
  void aSessionJustStartedIsCountedAndOpenImmediately() throws Exception {
    DocumentContext before = overview();
    assertThat(before.read("$.facts.sessionCount", Integer.class)).isEqualTo(1);

    UUID second = nextSession();

    DocumentContext doc = overview();
    assertThat(doc.read("$.facts.sessionCount", Integer.class)).isEqualTo(2);
    assertThat(doc.read("$.drafts.openSession.entryId", String.class)).isEqualTo(second.toString());
    assertThat(doc.read("$.sourceRev", Long.class)).isEqualTo(clinicalRev());
  }

  @Test
  void aDraftJustCreatedIsCountedImmediately() throws Exception {
    overview();
    Map<String, Object> draft = base("note", daysAgo(0));
    draft.put("status", "draft");
    draft.put("body", "Brouillon");
    post(draft);

    DocumentContext doc = overview();

    assertThat(doc.read("$.drafts.count", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.sourceRev", Long.class)).isEqualTo(clinicalRev());
  }

  @Test
  void aLinkJustCreatedIsReflectedImmediately() throws Exception {
    UUID situation = situation("Conflit au travail", daysAgo(10));
    UUID feeling = feeling("anger", 8, daysAgo(2));
    assertThat(overview().read("$.activeSituations[0].linkedFeelingCount", Integer.class)).isZero();

    send(
            MockMvcRequestBuilders.post("/api/entries/{id}/links", situation),
            Map.of("targetId", feeling.toString(), "linkType", "triggers"))
        .andExpect(status().isCreated());

    DocumentContext doc = overview();
    assertThat(doc.read("$.activeSituations[0].linkedFeelingCount", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.activeSituations[0].linkCount", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.activeSituations[0].averageIntensity", Double.class)).isEqualTo(8.0);
    assertThat(doc.read("$.sourceRev", Long.class)).isEqualTo(clinicalRev());
  }

  @Test
  void anArchivedEntryDisappearsImmediately() throws Exception {
    UUID id = feeling("joy", 9, daysAgo(1));
    assertThat(overview("window", "30d").read("$.emotions.top[0].count", Integer.class))
        .isEqualTo(1);

    archive(id);

    DocumentContext doc = overview("window", "30d");
    assertThat(doc.read("$.emotions.top", List.class)).isEmpty();
    assertThat(doc.read("$.sourceRev", Long.class)).isEqualTo(clinicalRev());
  }

  @Test
  void aWriteWithoutAnyEventIsStillSeenBecauseTheRevisionMovedOn() throws Exception {
    // Aucun événement n'est publié : seul clinical_rev a bougé, comme si l'abonné était en panne.
    assertThat(overview().read("$.facts.sessionCount", Integer.class)).isEqualTo(1);
    long revBefore = clinicalRev();
    insertSessionBySql();
    touchRev();

    DocumentContext doc = overview();

    assertThat(doc.read("$.facts.sessionCount", Integer.class)).isEqualTo(2);
    assertThat(doc.read("$.sourceRev", Long.class))
        .isEqualTo(revBefore + 1)
        .isEqualTo(clinicalRev());
    assertThat(
            jdbc.queryForObject(
                "select source_rev from patient_overview where patient_id = ?",
                Long.class,
                patient))
        .as("la ligne est réécrite")
        .isEqualTo(clinicalRev());
  }

  @Test
  void aProjectionBehindTheRevisionIsRecomputedNotServed() throws Exception {
    overview();
    // Ligne volontairement fausse et en retard d'une révision.
    jdbc.update(
        "update patient_overview set source_rev = source_rev - 1,"
            + " payload = jsonb_set(payload, '{facts,sessionCount}', '99') where patient_id = ?",
        patient);

    DocumentContext doc = overview();

    assertThat(doc.read("$.facts.sessionCount", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.sourceRev", Long.class)).isEqualTo(clinicalRev());
    assertThat((Integer) JsonPath.read(storedPayload(patient), "$.facts.sessionCount"))
        .isEqualTo(1);
  }

  @Test
  void aProjectionComputedOnAnotherDayIsRecomputed() throws Exception {
    overview();
    jdbc.update(
        "update patient_overview set computed_at = ?,"
            + " payload = jsonb_set(payload, '{facts,sessionCount}', '99') where patient_id = ?",
        Timestamp.from(daysAgo(2)),
        patient);

    DocumentContext doc = overview();

    assertThat(doc.read("$.facts.sessionCount", Integer.class)).isEqualTo(1);
    assertThat(Instant.parse(doc.read("$.computedAt", String.class))).isEqualTo(NOW);
  }

  @Test
  void aPatientRecordChangeWithoutNewRevisionIsSeen() throws Exception {
    overview();
    jdbc.update(
        "insert into patient_flag (id, patient_id, kind, reason, set_by)"
            + " values (?, ?, 'risk', 'Évalué', ?)",
        UUID.randomUUID(),
        patient,
        me);

    DocumentContext doc = overview();

    assertThat(doc.read("$.facts.flags[0].kind", String.class)).isEqualTo("risk");
  }

  @Test
  void concurrentReadsAfterAWriteAllSeeTheWrite() throws Exception {
    feeling("fear", 6, daysAgo(1));
    long rev = clinicalRev();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Future<DocumentContext>> futures = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        Callable<DocumentContext> read = () -> overview("window", "30d");
        futures.add(pool.submit(read));
      }
      for (Future<DocumentContext> f : futures) {
        DocumentContext doc = f.get();
        assertThat(doc.read("$.emotions.top[0].code", String.class)).isEqualTo("fear");
        assertThat(doc.read("$.sourceRev", Long.class)).isEqualTo(rev);
      }
    } finally {
      pool.shutdownNow();
    }
    assertThat(count("select count(*) from patient_overview where patient_id = ?", patient))
        .isEqualTo(1);
  }

  /** Séance posée par SQL, comme le ferait un import : entrée, version et détail. */
  private void insertSessionBySql() {
    UUID entryId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    tx.executeWithoutResult(
        s -> {
          jdbc.update(
              "insert into entry (id, tenant_id, patient_id, type, occurred_from, occurred_to,"
                  + " current_version_id, created_by) values (?, ?, ?, 'session', ?, ?, ?, ?)",
              entryId,
              tenant,
              patient,
              Timestamp.from(daysAgo(7)),
              Timestamp.from(daysAgo(7)),
              versionId,
              me);
          jdbc.update(
              "insert into entry_version (id, entry_id, version, body, author_id)"
                  + " values (?, ?, 1, 'Séance importée', ?)",
              versionId,
              entryId,
              me);
          jdbc.update(
              "insert into entry_session (entry_id, kind, modality, duration_min, closed_at)"
                  + " values (?, 'session', 'in_person', 50, ?)",
              entryId,
              Timestamp.from(daysAgo(7)));
        });
  }
}
