package com.psymate.overview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * M4-06 : résumé rédigé par règles et payload enrichi. Données créées par l'API de clinical,
 * Postgres réel (Testcontainers), horloge fixée à 2026-10-29T12:00Z.
 */
class OverviewNarrativeIT extends OverviewSupport {

  private static final List<String> FORBIDDEN =
      List.of(
          "trouble",
          "diagnostic",
          "risque",
          "pathologie",
          "disorder",
          "diagnosis",
          "risk",
          "pathology");

  // ---- données

  /** Deux ressentis d'anxiété par semaine : les 30 jours précédents puis les 30 derniers. */
  private void seedAnxiety(int[] previous, int[] recent) throws Exception {
    int[] previousDays = {59, 56, 52, 49, 45, 42, 38, 35, 31, 30};
    int[] recentDays = {29, 26, 22, 19, 15, 12, 8, 5, 2, 1};
    for (int i = 0; i < previousDays.length; i++) {
      feeling("anxiety", previous[i % previous.length], daysAgo(previousDays[i]));
    }
    for (int i = 0; i < recentDays.length; i++) {
      feeling("anxiety", recent[i % recent.length], daysAgo(recentDays[i]));
    }
  }

  private DocumentContext overviewIn(String language, String... nameValue) throws Exception {
    var get = MockMvcRequestBuilders.get("/api/patients/{id}/overview", patient);
    for (int i = 0; i < nameValue.length; i += 2) {
      get.queryParam(nameValue[i], nameValue[i + 1]);
    }
    if (language != null) {
      get.header("Accept-Language", language);
    }
    return JsonPath.parse(body(mvc.perform(get.with(auth)).andExpect(status().isOk())));
  }

  private static List<String> sentences(DocumentContext doc) {
    return doc.read("$.narrative.sentences", List.class);
  }

  private static String text(DocumentContext doc) {
    return String.join(" ", sentences(doc));
  }

  private static void assertNoForbidden(String text) {
    String plain =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    for (String word : FORBIDDEN) {
      assertThat(plain).as("terme interdit « %s » dans : %s", word, text).doesNotContain(word);
    }
  }

  private UUID event(String title, Instant from) throws Exception {
    Map<String, Object> payload = base("event", from);
    payload.put("title", title);
    return post(payload);
  }

  private void closeWithSummary(UUID sessionId, String summary) throws Exception {
    send(
            MockMvcRequestBuilders.post("/api/sessions/{id}/close", sessionId),
            Map.of("durationMin", 50, "summary", summary))
        .andExpect(status().isOk());
  }

  // ---- récit : tendance

  @Test
  void risingAnxietyOverSixtyDaysSaysItGoesUp() throws Exception {
    seedAnxiety(new int[] {3, 4}, new int[] {7, 8});

    DocumentContext doc = overview();

    assertThat(sentences(doc)).anyMatch(s -> s.contains("monte"));
    assertThat(text(doc)).contains("Anxiété").contains("7,5").contains("3,5");
    assertThat(sentences(doc).size()).isBetween(2, 5);
    assertThat(doc.read("$.narrative.attention", Object.class)).isNull();
  }

  @Test
  void flatSeedSaysStable() throws Exception {
    seedAnxiety(new int[] {5, 6}, new int[] {5, 6});

    assertThat(sentences(overview())).anyMatch(s -> s.contains("reste stable"));
  }

  @Test
  void descendingSeedSaysItGoesDown() throws Exception {
    seedAnxiety(new int[] {8, 7}, new int[] {3, 4});

    assertThat(sentences(overview())).anyMatch(s -> s.contains("baisse"));
  }

  @Test
  void noFeelingsAtAllGivesNotEnoughFeelings() throws Exception {
    assertThat(text(overview())).contains("Pas assez de ressentis pour dégager une tendance");
  }

  @Test
  void onlyCurrentWindowGivesAverageWithoutTrendVerb() throws Exception {
    feeling("anxiety", 6, daysAgo(3));
    feeling("anxiety", 8, daysAgo(10));

    String text = text(overview());

    assertThat(text).contains("Anxiété").contains("7");
    assertThat(text).doesNotContain("monte").doesNotContain("baisse").doesNotContain("stable");
  }

  @Test
  void acceptLanguageEnglishGivesEnglishSentencesAndFrenchIsTheDefault() throws Exception {
    seedAnxiety(new int[] {3, 4}, new int[] {7, 8});
    situation("Job loss", daysAgo(20));
    medication("sertraline", "50 mg", daysAgo(15));

    DocumentContext en = overviewIn("en");
    DocumentContext fr = overviewIn(null);
    DocumentContext de = overviewIn("de");

    assertThat(text(en)).contains("going up").contains("Anxiety").contains("Followed since");
    assertThat(text(en)).contains("One situation is still open: Job loss.");
    assertThat(text(en)).contains("Current treatment: sertraline 50 mg, since");
    assertThat(text(en)).doesNotContain("monte").doesNotContain("Suivi depuis");
    assertThat(text(fr)).contains("monte").contains("Suivi depuis").contains("Traitement en cours");
    assertThat(text(de)).contains("monte");
  }

  @Test
  void theNarrativeFollowsTheRequestLanguageEvenOnAWarmProjection() throws Exception {
    seedAnxiety(new int[] {3, 4}, new int[] {7, 8});
    // la même ligne stockée sert les deux langues : aucun texte n'est figé en base
    assertThat(text(overviewIn("en"))).contains("going up");
    assertThat(text(overviewIn(null))).contains("monte");
    assertThat(storedPayload(patient)).doesNotContain("monte").doesNotContain("going up");
  }

  // ---- récit : cadre, situations, traitement

  @Test
  void frameMentionsFollowUpStartSessionCountAndRhythm() throws Exception {
    // 5 séances hebdomadaires closes, la sixième ouverte : base (NOW) puis +7 j, ...
    jdbc.update(
        "update entry set occurred_from = ? where id = ?",
        java.sql.Timestamp.from(daysAgo(60)),
        session);
    touchRev();
    for (int i = 1; i <= 5; i++) {
      closeWithSummary(session, "Séance " + i);
      clock.set(daysAgo(60 - 7 * i));
      session = UUID.fromString(startSession());
    }
    clock.set(NOW);

    String text = text(overview());

    assertThat(text).contains("Suivi depuis le").contains("6 séances");
    assertThat(text).contains("environ une séance par semaine");
  }

  @Test
  void rhythmIsOmittedWithFewerThanThreeSessions() throws Exception {
    String text = text(overview());

    assertThat(text).contains("une séance.").doesNotContain("par semaine");
  }

  @Test
  void situationsAreListedUpToThreeThenCounted() throws Exception {
    DocumentContext none = overview();
    assertThat(text(none)).doesNotContain("situation");

    situation("Licenciement", daysAgo(30));
    assertThat(text(overview())).contains("Une situation reste ouverte : Licenciement.");

    situation("Déménagement", daysAgo(20));
    situation("Deuil", daysAgo(10));
    assertThat(text(overview()))
        .containsPattern("Des situations restent ouvertes : [^.]+, [^.]+ et [^.]+\\.");

    situation("Conflit", daysAgo(9));
    situation("Insomnie", daysAgo(8));
    String many = text(overview());
    assertThat(many).contains("et 2 autres.");
  }

  // ---- récit : événement non commenté

  @Test
  void anUncommentedEventGetsItsOwnSentence() throws Exception {
    event("Dispute avec le frère", daysAgo(5));

    String text = text(overview());

    assertThat(text).contains("Dispute avec le frère").contains("pas encore été commenté");
    assertThat(text).contains("24 octobre 2026");
  }

  @Test
  void aLinkedFeelingOrAFeelingAfterTheEventSilencesIt() throws Exception {
    // ressenti antérieur mais relié : le lien suffit
    UUID linkedEvent = event("Dispute avec le frère", daysAgo(5));
    UUID earlier = feeling("anger", 6, daysAgo(8));
    assertThat(text(overview())).contains("pas encore été commenté");
    send(
            MockMvcRequestBuilders.post("/api/entries/{id}/links", linkedEvent),
            Map.of("targetId", earlier.toString(), "linkType", "triggers"))
        .andExpect(status().isCreated());
    assertThat(text(overview())).doesNotContain("pas encore été commenté");
  }

  @Test
  void aFeelingCreatedWithTheLinkOfTheEventSilencesIt() throws Exception {
    UUID event = event("Dispute avec le frère", daysAgo(7));
    feeling(
        "anger",
        6,
        daysAgo(9),
        "clinical",
        List.of(
            Map.of(
                "targetId", event.toString(), "linkType", "caused_by", "direction", "outgoing")));
    assertThat(text(overview())).doesNotContain("pas encore été commenté");
  }

  @Test
  void aLaterUnlinkedFeelingSilencesItButAnEarlierOneDoesNot() throws Exception {
    event("Dispute avec le frère", daysAgo(5));
    feeling("anger", 6, daysAgo(8));
    assertThat(text(overview())).contains("pas encore été commenté");

    feeling("sadness", 4, daysAgo(2));
    assertThat(text(overview())).doesNotContain("pas encore été commenté");
  }

  @Test
  void anOldOrArchivedEventGivesNoSentence() throws Exception {
    event("Ancienne dispute", daysAgo(45));
    UUID archived = event("Dispute archivée", daysAgo(3));
    archive(archived);

    assertThat(text(overview())).doesNotContain("pas encore été commenté");
  }

  @Test
  void theMostRecentUncommentedEventIsNamed() throws Exception {
    event("Premier événement", daysAgo(20));
    event("Second événement", daysAgo(6));

    String text = text(overview());

    assertThat(text).contains("Second événement").doesNotContain("Premier événement");
  }

  // ---- récit : drapeaux

  @Test
  void anActiveRiskFlagGivesAnAttentionWithoutTheWordRisk() throws Exception {
    assertThat(overview().read("$.narrative.attention", Object.class)).isNull();

    jdbc.update(
        "insert into patient_flag (id, patient_id, kind, reason, set_by)"
            + " values (?, ?, 'risk', 'Sommeil très perturbé', ?)",
        UUID.randomUUID(),
        patient,
        me);

    DocumentContext doc = overview();
    String attention = doc.read("$.narrative.attention", String.class);
    assertThat(attention).contains("Vigilance particulière").contains("Sommeil très perturbé");
    assertNoForbidden(attention);
    assertThat(overviewIn("en").read("$.narrative.attention", String.class))
        .contains("Particular vigilance")
        .doesNotContainIgnoringCase("risk");
  }

  @Test
  void clearingTheFlagRemovesTheAttention() throws Exception {
    jdbc.update(
        "insert into patient_flag (id, patient_id, kind, reason, set_by)"
            + " values (?, ?, 'watch', 'Rupture récente', ?)",
        UUID.randomUUID(),
        patient,
        me);
    assertThat(overview().read("$.narrative.attention", String.class)).contains("À surveiller");

    jdbc.update(
        "update patient_flag set cleared_at = now() + interval '1 second', cleared_by = ?"
            + " where patient_id = ?",
        me,
        patient);

    assertThat(overview().read("$.narrative.attention", Object.class)).isNull();
  }

  @Test
  void noGeneratedSentenceContainsAForbiddenTerm() throws Exception {
    seedAnxiety(new int[] {3, 4}, new int[] {7, 8});
    situation("Licenciement", daysAgo(20));
    event("Dispute avec le frère", NOW.minus(1, ChronoUnit.HOURS));
    medication("sertraline", "50 mg", daysAgo(15));
    for (String kind : List.of("watch", "risk", "shared_care")) {
      jdbc.update(
          "insert into patient_flag (id, patient_id, kind, reason, set_by)"
              + " values (?, ?, ?, 'Motif noté', ?)",
          UUID.randomUUID(),
          patient,
          kind,
          me);
    }
    for (String language : new String[] {null, "en"}) {
      DocumentContext doc = overviewIn(language);
      assertThat(sentences(doc).size()).isBetween(3, 5);
      sentences(doc).forEach(OverviewNarrativeIT::assertNoForbidden);
      assertNoForbidden(doc.read("$.narrative.attention", String.class));
    }
  }

  // ---- feelings

  @Test
  void feelingsHaveExactlyOnePointPerNonArchivedFeelingOfTheWindow() throws Exception {
    UUID f5 = feeling("anxiety", 5, daysAgo(5));
    UUID f20 = feeling("joy", 6, daysAgo(20));
    UUID priv = feeling("calm", 4, daysAgo(15), "private", null);
    UUID f40 = feeling("anxiety", 7, daysAgo(40));
    UUID f70 = feeling("sadness", 3, daysAgo(70));
    UUID f200 = feeling("anger", 8, daysAgo(200));
    archive(feeling("anxiety", 10, daysAgo(3)));
    UUID lifePhase = feeling("anger", 9, daysAgo(10));
    jdbc.update("update entry set occurred_precision = 'life_phase' where id = ?", lifePhase);
    feeling("joy", 2, daysAgo(400));
    touchRev();

    DocumentContext d30 = overview("window", "30d");
    DocumentContext d90 = overview("window", "90d");
    DocumentContext m12 = overview("window", "12m");

    assertThat(d30.read("$.feelings.points[*].entryId", List.class))
        .containsExactlyInAnyOrder(f5.toString(), f20.toString(), priv.toString());
    assertThat(d90.read("$.feelings.points[*].entryId", List.class))
        .containsExactlyInAnyOrder(
            f5.toString(), f20.toString(), priv.toString(), f40.toString(), f70.toString());
    assertThat(m12.read("$.feelings.points[*].entryId", List.class))
        .containsExactlyInAnyOrder(
            f5.toString(),
            f20.toString(),
            priv.toString(),
            f40.toString(),
            f70.toString(),
            f200.toString());

    // du plus ancien au plus récent, avec émotion, famille, intensité et séance
    assertThat(d30.read("$.feelings.points[*].date", List.class))
        .containsExactly(
            today().minusDays(20).toString(),
            today().minusDays(15).toString(),
            today().minusDays(5).toString());
    assertThat(d30.read("$.feelings.points[0].emotionCode", String.class)).isEqualTo("joy");
    assertThat(d30.read("$.feelings.points[0].family", String.class)).isEqualTo("joy");
    assertThat(d30.read("$.feelings.points[0].intensity", Integer.class)).isEqualTo(6);
    assertThat(d30.read("$.feelings.points[0].sessionId", String.class))
        .isEqualTo(session.toString());
    assertThat(Instant.parse(d30.read("$.feelings.points[2].at", String.class)))
        .isEqualTo(daysAgo(5));
  }

  @Test
  void feelingsHaveOneSeriePerEmotionPresentInTheWindow() throws Exception {
    feeling("anxiety", 5, daysAgo(5));
    feeling("anxiety", 6, daysAgo(6));
    feeling("joy", 6, daysAgo(20));
    feeling("sadness", 3, daysAgo(70));

    DocumentContext d30 = overview("window", "30d");
    DocumentContext d90 = overview("window", "90d");

    assertThat(d30.read("$.feelings.series[*].emotionCode", List.class))
        .containsExactly("anxiety", "joy");
    assertThat(d30.read("$.feelings.series[0].count", Integer.class)).isEqualTo(2);
    assertThat(d30.read("$.feelings.series[0].label", String.class)).isEqualTo("Anxiété");
    assertThat(d30.read("$.feelings.series[0].labelEn", String.class)).isEqualTo("Anxiety");
    assertThat(d30.read("$.feelings.series[0].family", String.class)).isEqualTo("fear");
    assertThat(d90.read("$.feelings.series[*].emotionCode", List.class))
        .containsExactly("anxiety", "joy", "sadness");
  }

  @Test
  void aPeriodFeelingIsOnePointCappedAtTodayWithItsOwnStart() throws Exception {
    Map<String, Object> payload = base("feeling", daysAgo(2));
    payload.put(
        "occurred",
        Map.of(
            "from",
            daysAgo(2).toString(),
            "to",
            NOW.plus(30, ChronoUnit.DAYS).toString(),
            "precision",
            "period",
            "label",
            "ces semaines-ci"));
    payload.put("feeling", Map.of("emotionCode", "fear", "intensity", 7));
    UUID id = post(payload);

    DocumentContext doc = overview("window", "30d");

    assertThat(doc.read("$.feelings.points", List.class)).hasSize(1);
    assertThat(doc.read("$.feelings.points[0].entryId", String.class)).isEqualTo(id.toString());
    assertThat(doc.read("$.feelings.points[0].date", String.class)).isEqualTo(today().toString());
    assertThat(Instant.parse(doc.read("$.feelings.points[0].at", String.class)))
        .isEqualTo(daysAgo(2));
  }

  @Test
  void feelingsCarryMedicationMarkersAndSessionTicksOfTheWindow() throws Exception {
    medication("sertraline", "50 mg", daysAgo(10));
    medication("lorazepam", "1 mg", daysAgo(60));

    DocumentContext d30 = overview("window", "30d");
    DocumentContext d90 = overview("window", "90d");

    assertThat(d30.read("$.feelings.markers[*].kind", List.class)).containsOnly("medication");
    assertThat(d30.read("$.feelings.markers[*].label", List.class))
        .containsExactly("sertraline 50 mg");
    assertThat(d90.read("$.feelings.markers[*].label", List.class))
        .containsExactly("lorazepam 1 mg", "sertraline 50 mg");
    // la séance ouverte du jour est un tick
    assertThat(d30.read("$.feelings.ticks", List.class)).containsExactly(today().toString());
  }

  // ---- sinceLastSession

  @Test
  void sinceLastSessionKeepsOnlyEntriesAfterTheLastClosedSession() throws Exception {
    jdbc.update(
        "update entry set occurred_from = ? where id = ?",
        java.sql.Timestamp.from(daysAgo(30)),
        session);
    touchRev();
    UUID inSession = feeling("anxiety", 5, daysAgo(29));
    UUID closedSession = session;
    closeWithSummary(closedSession, "Compte rendu");
    session = UUID.fromString(startSession());

    UUID earlier = situation("Avant la séance", daysAgo(40));
    UUID note = note("Entre deux séances", daysAgo(10));
    UUID recent = feeling("joy", 6, daysAgo(2));
    UUID archived = feeling("sadness", 3, daysAgo(4));
    archive(archived);
    Map<String, Object> draft = base("note", daysAgo(1));
    draft.put("status", "draft");
    draft.put("body", "Brouillon");
    UUID draftId = post(draft);

    DocumentContext doc = overview();

    assertThat(doc.read("$.sinceLastSession.lastSessionId", String.class))
        .isEqualTo(closedSession.toString());
    assertThat(Instant.parse(doc.read("$.sinceLastSession.lastSessionAt", String.class)))
        .isEqualTo(daysAgo(30));
    assertThat(doc.read("$.sinceLastSession.entries[*].entryId", List.class))
        .containsExactly(draftId.toString(), recent.toString(), note.toString());
    assertThat(doc.read("$.sinceLastSession.entries[*].entryId", List.class))
        .doesNotContain(
            inSession.toString(), earlier.toString(), archived.toString(), session.toString());
    assertThat(doc.read("$.sinceLastSession.entries[0].type", String.class)).isEqualTo("note");
    assertThat(doc.read("$.sinceLastSession.entries[0].status", String.class)).isEqualTo("draft");
    assertThat(doc.read("$.sinceLastSession.entries[1].visibility", String.class))
        .isEqualTo("clinical");
    assertThat(doc.read("$.sinceLastSession.entries[1].sessionId", String.class))
        .isEqualTo(session.toString());
    assertThat(doc.read("$.sinceLastSession.entries[2].title", Object.class)).isNull();
  }

  @Test
  void withoutAClosedSessionAllEntriesOutsideSessionsAreListedTwentyAtMost() throws Exception {
    for (int i = 0; i < 23; i++) {
      note("Note " + i, daysAgo(1 + i));
    }

    DocumentContext doc = overview();

    assertThat(doc.read("$.sinceLastSession.lastSessionId", Object.class)).isNull();
    assertThat(doc.read("$.sinceLastSession.lastSessionAt", Object.class)).isNull();
    assertThat(doc.read("$.sinceLastSession.entries", List.class)).hasSize(20);
    assertThat(doc.read("$.sinceLastSession.entries[*].type", List.class)).containsOnly("note");
  }

  // ---- recentSessions

  @Test
  void recentSessionsAreFiveAtMostTheLatestWithItsFullSummary() throws Exception {
    List<UUID> sessions = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      clock.set(NOW.plus(i, ChronoUnit.HOURS));
      if (i > 0) {
        session = UUID.fromString(startSession());
      }
      sessions.add(session);
      if (i == 6) {
        feeling("anxiety", 6, NOW.minus(1, ChronoUnit.DAYS));
        archive(feeling("joy", 5, NOW.minus(2, ChronoUnit.DAYS)));
        note("Une note de la dernière séance", NOW.minus(3, ChronoUnit.DAYS));
      }
      closeWithSummary(session, "Séance " + i + ": premier point\n\nDeuxième paragraphe " + i);
    }

    DocumentContext doc = overview();

    assertThat(doc.read("$.recentSessions", List.class)).hasSize(5);
    assertThat(doc.read("$.recentSessions[*].entryId", List.class))
        .containsExactly(
            sessions.get(6).toString(),
            sessions.get(5).toString(),
            sessions.get(4).toString(),
            sessions.get(3).toString(),
            sessions.get(2).toString());
    assertThat(doc.read("$.recentSessions[0].summary", String.class))
        .isEqualTo("Séance 6: premier point\n\nDeuxième paragraphe 6");
    assertThat(doc.read("$.recentSessions[0].summaryFirstLine", String.class))
        .isEqualTo("Séance 6: premier point");
    for (int i = 1; i < 5; i++) {
      assertThat(doc.read("$.recentSessions[" + i + "].summary", Object.class)).isNull();
      assertThat(doc.read("$.recentSessions[" + i + "].summaryFirstLine", String.class))
          .startsWith("Séance " + (6 - i));
    }
    assertThat(doc.read("$.recentSessions[0].entryCount", Integer.class)).isEqualTo(2);
    assertThat(doc.read("$.recentSessions[1].entryCount", Integer.class)).isZero();
    assertThat(doc.read("$.recentSessions[0].kind", String.class)).isEqualTo("session");
    assertThat(doc.read("$.recentSessions[0].modality", String.class)).isEqualTo("in_person");
    assertThat(doc.read("$.recentSessions[0].durationMin", Integer.class)).isEqualTo(50);
    assertThat(doc.read("$.recentSessions[0].closedAt", String.class)).isNotNull();
    assertThat(doc.read("$.facts.sessionCount", Integer.class)).isEqualTo(7);
  }

  @Test
  void theLatestOpenSessionHasNoSummaryAndFewSessionsAreAllListed() throws Exception {
    DocumentContext doc = overview();

    assertThat(doc.read("$.recentSessions", List.class)).hasSize(1);
    assertThat(doc.read("$.recentSessions[0].entryId", String.class)).isEqualTo(session.toString());
    assertThat(doc.read("$.recentSessions[0].closedAt", Object.class)).isNull();
    assertThat(doc.read("$.recentSessions[0].summary", Object.class)).isNull();
    assertThat(doc.read("$.recentSessions[0].summaryFirstLine", Object.class)).isNull();
  }

  @Test
  void archivedSessionsAreNotListed() throws Exception {
    UUID first = session;
    closeWithSummary(first, "Première");
    session = UUID.fromString(startSession());
    archive(first);

    DocumentContext doc = overview();

    assertThat(doc.read("$.recentSessions[*].entryId", List.class))
        .containsExactly(session.toString());
  }

  // ---- compatibilité avec les lignes écrites par M4-03

  @Test
  void aRowStoredBeforeM406IsRecomputedInsteadOfFailing() throws Exception {
    seedAnxiety(new int[] {3, 4}, new int[] {7, 8});
    overview();
    assertThat(storedPayload(patient)).contains("recentSessions").contains("narrativeFacts");
    awaitOverviewQuiet();

    // ligne M4-03 : même révision, même jour, mais sans les champs ajoutés par M4-06
    int updated =
        jdbc.update(
            "update patient_overview set payload = payload - 'recentSessions' - 'narrativeFacts'"
                + " - 'feelingPoints' - 'emotionCatalog' - 'sinceLastSession'"
                + " where patient_id = ?",
            patient);
    assertThat(updated).isEqualTo(1);
    assertThat(storedPayload(patient)).doesNotContain("recentSessions");

    DocumentContext doc = overview();

    assertThat(sentences(doc)).anyMatch(s -> s.contains("monte"));
    assertThat(doc.read("$.recentSessions", List.class)).hasSize(1);
    assertThat(doc.read("$.feelings.points", List.class)).isNotEmpty();
    assertThat(doc.read("$.sinceLastSession.entries", List.class)).isNotNull();
    assertThat(storedPayload(patient)).contains("recentSessions").contains("narrativeFacts");
  }

  @Test
  void aRowMissingOnlyRecentSessionsIsAlsoRecomputed() throws Exception {
    feeling("anxiety", 5, daysAgo(3));
    overview();
    awaitOverviewQuiet();
    jdbc.update(
        "update patient_overview set payload = payload - 'recentSessions' where patient_id = ?",
        patient);

    DocumentContext doc = overview();

    assertThat(doc.read("$.recentSessions", List.class)).hasSize(1);
    assertThat(doc.read("$.feelings.points", List.class)).hasSize(1);
  }
}
