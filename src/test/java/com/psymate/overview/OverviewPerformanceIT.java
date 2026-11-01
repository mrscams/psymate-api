package com.psymate.overview;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ouverture de la vue d'ensemble d'un patient de 2 000 entrées (A6 : cible 200 ms). La cible n'est
 * pas bloquante (la machine de test varie) : l'assertion ne vérifie que l'absence de dérive grave,
 * la mesure est journalisée et écrite dans target/overview-perf.txt.
 */
class OverviewPerformanceIT extends OverviewSupport {

  private static final Logger LOG = LoggerFactory.getLogger(OverviewPerformanceIT.class);
  private static final int ENTRIES = 2_000;
  private static final int WARM_REPEATS = 10;

  @Test
  void openingAPatientWithTwoThousandEntriesStaysFast() throws Exception {
    seedEntries();
    jdbc.execute("analyze entry");
    jdbc.execute("analyze entry_version");
    jdbc.execute("analyze entry_feeling");
    jdbc.execute("analyze link");
    touchRev();

    // Première ouverture : la projection est absente ou en retard, calcul à la volée.
    long coldStart = System.nanoTime();
    DocumentContext cold = overview();
    long coldMs = (System.nanoTime() - coldStart) / 1_000_000;
    assertThat(cold.read("$.trend.points", List.class)).hasSize(90);
    assertThat(cold.read("$.sourceRev", Long.class)).isEqualTo(clinicalRev());
    int computeMs =
        jdbc.queryForObject(
            "select compute_ms from patient_overview where patient_id = ?", Integer.class, patient);

    // Ouvertures suivantes : lecture de la projection, trois fenêtres en alternance.
    String[] windows = {"90d", "30d", "12m"};
    overview("window", "90d");
    List<Long> warm = new ArrayList<>();
    for (int i = 0; i < WARM_REPEATS; i++) {
      long start = System.nanoTime();
      overview("window", windows[i % windows.length]);
      warm.add((System.nanoTime() - start) / 1_000_000);
    }
    Collections.sort(warm);
    long warmP50 = warm.get(warm.size() / 2);
    long warmMax = warm.get(warm.size() - 1);

    String summary =
        "Vue d'ensemble "
            + (ENTRIES + 1)
            + " entrées : ouverture à froid (calcul) = "
            + coldMs
            + " ms (dont calcul SQL = "
            + computeMs
            + " ms) ; ouvertures suivantes : p50="
            + warmP50
            + " ms, max="
            + warmMax
            + " ms ; cible A6 : 200 ms";
    LOG.info(summary);
    System.out.println(summary);
    Files.writeString(
        Path.of("target", "overview-perf.txt"), summary + "\n", StandardCharsets.UTF_8);

    assertThat(coldMs).as("dérive grave (la cible 200 ms n'est pas bloquante)").isLessThan(5_000);
    assertThat(warmMax).as("dérive grave (la cible 200 ms n'est pas bloquante)").isLessThan(5_000);
  }

  private void seedEntries() {
    Random random = new Random(7);
    String[] emotions = {"anxiety", "joy", "sadness", "anger"};
    Instant start = daysAgo(365);
    List<Object[]> entries = new ArrayList<>();
    List<Object[]> versions = new ArrayList<>();
    List<Object[]> feelings = new ArrayList<>();
    List<Object[]> sessions = new ArrayList<>();
    List<Object[]> medications = new ArrayList<>();
    List<UUID> situationIds = new ArrayList<>();
    List<UUID> feelingIds = new ArrayList<>();
    for (int i = 0; i < ENTRIES; i++) {
      UUID entryId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();
      Timestamp when =
          Timestamp.from(start.plus(random.nextInt(365 * 24 * 60), ChronoUnit.MINUTES));
      String type;
      Timestamp to = when;
      UUID sessionId = session;
      String title = null;
      if (i % 40 == 0) {
        type = "session";
        sessionId = null;
        sessions.add(new Object[] {entryId, when});
        title = "Séance " + i;
      } else if (i % 25 == 0) {
        type = "situation";
        to = i % 50 == 0 ? null : when;
        situationIds.add(entryId);
        title = "Situation " + i;
      } else if (i % 100 == 1) {
        type = "medication";
        to = i % 200 == 1 ? null : when;
        medications.add(new Object[] {entryId, "molécule " + i});
        title = "Médicament " + i;
      } else if (i % 5 == 0) {
        type = "note";
        title = "Note " + i;
      } else {
        type = "feeling";
        feelings.add(
            new Object[] {entryId, emotions[random.nextInt(emotions.length)], random.nextInt(11)});
        feelingIds.add(entryId);
      }
      entries.add(
          new Object[] {entryId, tenant, patient, type, when, to, sessionId, versionId, me});
      versions.add(new Object[] {versionId, entryId, title, "Texte de l'entrée " + i, me});
    }
    List<Object[]> links = new ArrayList<>();
    for (int i = 0; i < situationIds.size(); i++) {
      for (int k = 0; k < 4; k++) {
        links.add(
            new Object[] {
              UUID.randomUUID(),
              tenant,
              patient,
              situationIds.get(i),
              feelingIds.get((i * 7 + k) % feelingIds.size())
            });
      }
    }
    tx.executeWithoutResult(
        s -> {
          jdbc.batchUpdate(
              "insert into entry (id, tenant_id, patient_id, type, occurred_from, occurred_to,"
                  + " session_id, current_version_id, created_by)"
                  + " values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
              entries);
          jdbc.batchUpdate(
              "insert into entry_version (id, entry_id, version, title, body, author_id)"
                  + " values (?, ?, 1, ?, ?, ?)",
              versions);
          jdbc.batchUpdate(
              "insert into entry_feeling (entry_id, emotion_code, intensity) values (?, ?, ?)",
              feelings);
          jdbc.batchUpdate(
              "insert into entry_session (entry_id, kind, modality, duration_min, closed_at)"
                  + " values (?, 'session', 'in_person', 50, ?)",
              sessions);
          jdbc.batchUpdate(
              "insert into entry_medication (entry_id, molecule, dosage) values (?, ?, '10 mg')",
              medications);
          jdbc.batchUpdate(
              "insert into link (id, tenant_id, patient_id, source_id, target_id, link_type,"
                  + " provenance, status) values (?, ?, ?, ?, ?, 'triggers', 'manual',"
                  + " 'accepted')",
              links);
        });
    assertThat(count("select count(*) from entry where patient_id = ?", patient))
        .isEqualTo(ENTRIES + 1);
  }
}
