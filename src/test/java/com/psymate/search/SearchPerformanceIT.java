package com.psymate.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recherche sur 5 000 entrées d'un patient (A6 : cible 300 ms au 95e centile). La cible n'est pas
 * bloquante ici (la machine de test varie) : l'assertion ne vérifie que l'absence de dérive grave,
 * la mesure est journalisée et écrite dans target/search-perf.txt.
 */
class SearchPerformanceIT extends SearchSupport {

  private static final Logger LOG = LoggerFactory.getLogger(SearchPerformanceIT.class);
  private static final int ENTRIES = 5_000;
  private static final int REPEATS = 4;

  /** Vocabulaire d'environ 200 mots français (le mot « travail » est volontairement partout). */
  private static final String[] WORDS =
      ("""
      matin soir nuit jour semaine mois année famille mère père frère soeur enfant ami collègue
      manager équipe réunion projet bureau télétravail fatigue sommeil insomnie réveil angoisse
      peur colère tristesse joie calme tension stress pression repos vacances voyage maison
      appartement déménagement quartier voisin bruit silence musique lecture film sport marche
      course natation yoga méditation respiration douleur dos tête ventre cœur poitrine gorge
      appétit repas cuisine café thé alcool tabac médicament traitement dosage ordonnance rendez-vous
      séance thérapie parole silence souvenir enfance adolescence école études diplôme examen
      échec réussite promotion licenciement démission contrat salaire dette argent loyer facture
      couple mariage séparation divorce dispute reproche confiance jalousie solitude isolement
      deuil perte absence manque vide peur doute culpabilité honte fierté espoir projet avenir
      passé présent habitude rituel routine changement difficulté obstacle progrès amélioration
      rechute crise urgence hôpital médecin psychiatre infirmière pharmacie analyse bilan diagnostic
      objectif demande besoin envie limite refus accord compromis conflit tension apaisement
      confidence aveu secret mensonge vérité rêve cauchemar image pensée idée obsession ruminer
      attention concentration mémoire oubli retard fatigue lassitude énergie motivation courage
      patience colère rancune pardon regret nostalgie surprise inquiétude soulagement reconnaissance
      """)
          .split("\\s+");

  @Test
  void searchOnFiveThousandEntriesStaysFast() throws Exception {
    UUID tagWork = UUID.fromString(createTag("travail"));
    UUID tagFamily = UUID.fromString(createTag("famille"));
    seedEntries(tagWork, tagFamily);

    // Cas mesurés : mot fréquent, mot rare, deux mots, filtre + mot, mot mal orthographié.
    List<String[]> cases =
        List.of(
            new String[] {"q", "travail"},
            new String[] {"q", "ophtalmologue"},
            new String[] {"q", "travail fatigue sommeil"},
            new String[] {"q", "tag:travail fatigue", "types", "note"},
            new String[] {"q", "licencimen"});
    List<Long> clientMs = new ArrayList<>();
    List<Long> serverMs = new ArrayList<>();
    StringBuilder report = new StringBuilder();
    for (String[] c : cases) {
      search(c); // chauffe le plan et les caches
      List<Long> own = new ArrayList<>();
      for (int i = 0; i < REPEATS; i++) {
        long start = System.nanoTime();
        DocumentContext doc = search(c);
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        own.add(elapsed);
        clientMs.add(elapsed);
        serverMs.add(doc.read("$.tookMs", Long.class));
      }
      DocumentContext doc = search(c);
      report
          .append(String.format("%-45s", Arrays.toString(c)))
          .append(" total=")
          .append(doc.read("$.total", Long.class))
          .append(" approx=")
          .append(doc.read("$.approximateCount", Integer.class))
          .append(" ms=")
          .append(own)
          .append('\n');
    }
    Collections.sort(clientMs);
    Collections.sort(serverMs);
    long p50 = clientMs.get(clientMs.size() / 2);
    long max = clientMs.get(clientMs.size() - 1);
    long serverP50 = serverMs.get(serverMs.size() / 2);
    long serverMax = serverMs.get(serverMs.size() - 1);
    String summary =
        "Recherche "
            + ENTRIES
            + " entrées : p50="
            + p50
            + " ms, max="
            + max
            + " ms (serveur : p50="
            + serverP50
            + " ms, max="
            + serverMax
            + " ms) ; cible A6 : 300 ms";
    LOG.info(summary);
    System.out.println(summary);
    System.out.println(report);
    Files.writeString(
        Path.of("target", "search-perf.txt"), summary + "\n" + report, StandardCharsets.UTF_8);

    assertThat(max).as("dérive grave (la cible 300 ms n'est pas bloquante)").isLessThan(5_000);
  }

  private void seedEntries(UUID tagWork, UUID tagFamily) {
    Random random = new Random(42);
    String[] types = {"note", "note", "note", "event", "situation", "feeling"};
    String[] emotions = {"anxiety", "joy", "sadness", "anger"};
    Instant start = Instant.now().minus(3 * 365L, ChronoUnit.DAYS);
    List<Object[]> entries = new ArrayList<>();
    List<Object[]> versions = new ArrayList<>();
    List<Object[]> feelings = new ArrayList<>();
    List<Object[]> entryTags = new ArrayList<>();
    for (int i = 0; i < ENTRIES; i++) {
      UUID entryId = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();
      String type = types[random.nextInt(types.length)];
      Timestamp when =
          Timestamp.from(start.plus(random.nextInt(3 * 365 * 24 * 60), ChronoUnit.MINUTES));
      StringBuilder body = new StringBuilder("travail");
      int words = 25 + random.nextInt(40);
      for (int w = 0; w < words; w++) {
        body.append(' ').append(WORDS[random.nextInt(WORDS.length)]);
      }
      if (i % 1_700 == 0) {
        body.append(" chez l'ophtalmologue");
      }
      String title =
          "feeling".equals(type) || "note".equals(type) ? null : "Titre " + WORDS[i % WORDS.length];
      entries.add(
          new Object[] {entryId, tenant, patient, type, when, when, session, versionId, me});
      versions.add(new Object[] {versionId, entryId, title, body.toString(), me});
      if ("feeling".equals(type)) {
        feelings.add(new Object[] {entryId, emotions[random.nextInt(emotions.length)]});
      }
      if (i % 3 == 0) {
        entryTags.add(new Object[] {entryId, tagWork});
      }
      if (i % 5 == 0) {
        entryTags.add(new Object[] {entryId, tagFamily});
      }
    }
    tx.executeWithoutResult(
        s -> {
          jdbc.batchUpdate(
              "insert into entry (id, tenant_id, patient_id, type, occurred_from, occurred_to,"
                  + " session_id, current_version_id, created_by)"
                  + " values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
              entries);
          // Le trigger de V08 calcule le vecteur à l'insertion de chaque version courante.
          jdbc.batchUpdate(
              "insert into entry_version (id, entry_id, version, title, body, author_id)"
                  + " values (?, ?, 1, ?, ?, ?)",
              versions);
          jdbc.batchUpdate(
              "insert into entry_feeling (entry_id, emotion_code, intensity) values (?, ?, 5)",
              feelings);
          jdbc.batchUpdate("insert into entry_tag (entry_id, tag_id) values (?, ?)", entryTags);
        });
    // Tags et émotions arrivent après la version : rafraîchissement en masse, comme les abonnés.
    jdbc.execute(
        "select entry_search_vector_refresh(e.id) from entry e where e.patient_id = '"
            + patient
            + "'");
    jdbc.execute("analyze entry");
    jdbc.execute("analyze entry_version");
    jdbc.execute("analyze entry_tag");
    jdbc.execute("analyze entry_feeling");
    assertThat(
            count(
                "select count(*) from entry where patient_id = ? and search_vector is not null",
                patient))
        .isEqualTo(ENTRIES + 1);
  }
}
