package com.psymate.overview.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.psymate.overview.OverviewSupport;
import com.psymate.testsupport.SharedPostgres;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Contenu et forme de la vue d'ensemble (M4-03) : faits clés, situations actives, tendance,
 * émotions dominantes, brouillons, fenêtres, erreurs et activité récente. Les données sont créées
 * par l'API de clinical ; chaque lecture passe par GET /api/patients/{id}/overview.
 */
@Import(SharedPostgres.class)
class OverviewProjectionIT extends OverviewSupport {

  private UUID licenciement;
  private UUID demenagement;
  private UUID sertraline;
  private UUID lorazepam;
  private UUID zopiclone;
  private UUID f1;
  private UUID f2;

  /**
   * Jeu de données commun (jours avant aujourd'hui) : deux séances ; ressentis anxiété 8 (-3) et 4
   * (-10), joie 6 (-40), anxiété 5 (-100), tristesse privée 2 (-1) ; un ressenti archivé (-2) et un
   * ressenti de phase de vie (-5), tous deux exclus ; situation « Licenciement » (-20) liée aux
   * deux premiers ressentis, situation « Déménagement » (-5) sans lien, une situation terminée et
   * une archivée ; sertraline en cours (-60), lorazepam arrêté (-80 à -35), zopiclone dont
   * period_to est passé (-50, fin -20) ; un brouillon ; un drapeau « à surveiller ».
   */
  private void seed() throws Exception {
    nextSession();
    licenciement = situation("Licenciement", daysAgo(20));
    demenagement = situation("Déménagement", daysAgo(5));
    Map<String, Object> closed = withTo(base("situation", daysAgo(50)), daysAgo(40));
    closed.put("title", "Terminée");
    post(closed);
    archive(post(withTitle(base("situation", daysAgo(15)), "Archivée")));

    f1 =
        feeling(
            "anxiety",
            8,
            daysAgo(3),
            "clinical",
            List.of(
                Map.of(
                    "targetId",
                    licenciement.toString(),
                    "linkType",
                    "triggers",
                    "direction",
                    "incoming")));
    f2 =
        feeling(
            "anxiety",
            4,
            daysAgo(10),
            "clinical",
            List.of(
                Map.of(
                    "targetId",
                    licenciement.toString(),
                    "linkType",
                    "caused_by",
                    "direction",
                    "outgoing")));
    feeling("joy", 6, daysAgo(40));
    feeling("anxiety", 5, daysAgo(100));
    feeling("sadness", 2, daysAgo(1), "private", null);
    archive(feeling("anxiety", 10, daysAgo(2)));
    UUID lifePhase = feeling("anger", 9, daysAgo(5));
    jdbc.update("update entry set occurred_precision = 'life_phase' where id = ?", lifePhase);

    // Tous créés par l'API : sertraline sans fin saisie (en cours), lorazepam avec une fin
    // (occurred.to), zopiclone avec une période de prise terminée (period_to).
    sertraline = medication("sertraline", "50 mg", daysAgo(60));
    lorazepam = post(withTo(medicationPayload("lorazepam", daysAgo(80)), daysAgo(35)));
    zopiclone =
        post(
            medicationPayload(
                "zopiclone", daysAgo(50), today().minusDays(50), today().minusDays(20)));

    Map<String, Object> draft = base("note", daysAgo(1));
    draft.put("status", "draft");
    draft.put("body", "Brouillon de note");
    post(draft);

    jdbc.update(
        "insert into patient_flag (id, patient_id, kind, reason, set_by)"
            + " values (?, ?, 'watch', 'Idées noires évoquées', ?)",
        UUID.randomUUID(),
        patient,
        me);
    touchRev();
  }

  private Map<String, Object> withTo(Map<String, Object> payload, Instant to) {
    @SuppressWarnings("unchecked")
    Map<String, Object> occurred = (Map<String, Object>) payload.get("occurred");
    payload.put(
        "occurred",
        Map.of("from", occurred.get("from"), "to", to.toString(), "precision", "exact"));
    return payload;
  }

  private Map<String, Object> withTitle(Map<String, Object> payload, String title) {
    payload.put("title", title);
    payload.put("occurred", Map.of("from", ((Map<?, ?>) payload.get("occurred")).get("from")));
    return payload;
  }

  private Map<String, Object> medicationPayload(String molecule, Instant from) {
    return medicationPayload(molecule, from, null, null);
  }

  private Map<String, Object> medicationPayload(
      String molecule, Instant from, LocalDate periodFrom, LocalDate periodTo) {
    Map<String, Object> payload = base("medication", from);
    Map<String, Object> medication = new java.util.LinkedHashMap<>();
    medication.put("molecule", molecule);
    medication.put("dosage", "1 mg");
    if (periodFrom != null) {
      medication.put("periodFrom", periodFrom.toString());
    }
    if (periodTo != null) {
      medication.put("periodTo", periodTo.toString());
    }
    payload.put("medication", medication);
    return payload;
  }

  // ---- faits clés

  @Test
  void factsCarryAgeFollowedSinceSessionCountMedicationsAndFlags() throws Exception {
    seed();

    DocumentContext doc = overview();

    assertThat(doc.read("$.facts.age", Integer.class))
        .isEqualTo(Period.between(LocalDate.of(1990, 4, 2), today()).getYears());
    // Sans follow_up.since : le jour de création du dossier (horloge de la base).
    assertThat(doc.read("$.facts.followedSince", String.class))
        .isEqualTo(
            jdbc.queryForObject(
                "select to_char(created_at at time zone 'UTC', 'YYYY-MM-DD') from patient"
                    + " where id = ?",
                String.class,
                patient));
    assertThat(doc.read("$.facts.sessionCount", Integer.class)).isEqualTo(2);
    assertThat(doc.read("$.facts.activeMedications", List.class)).hasSize(1);
    assertThat(doc.read("$.facts.activeMedications[0].entryId", String.class))
        .isEqualTo(sertraline.toString());
    assertThat(doc.read("$.facts.activeMedications[0].molecule", String.class))
        .isEqualTo("sertraline");
    assertThat(doc.read("$.facts.activeMedications[0].dosage", String.class)).isEqualTo("50 mg");
    assertThat(doc.read("$.facts.activeMedications[0].frequency", String.class)).isEqualTo("matin");
    assertThat(doc.read("$.facts.activeMedications[0].prescriber", String.class)).isEqualTo("Dr X");
    assertThat(doc.read("$.facts.activeMedications[0].since", String.class)).isNotBlank();
    assertThat(doc.read("$.facts.flags", List.class)).hasSize(1);
    assertThat(doc.read("$.facts.flags[0].kind", String.class)).isEqualTo("watch");
    assertThat(doc.read("$.facts.flags[0].reason", String.class))
        .isEqualTo("Idées noires évoquées");
    assertThat(doc.read("$.facts.flags[0].setAt", String.class)).isNotBlank();
  }

  @Test
  void followedSinceComesFromFollowUpAndAgeIsNullWithoutBirthDate() throws Exception {
    jdbc.update(
        "update patient set follow_up = '{\"since\":\"2024-01-15\"}'::jsonb, birth_date = null,"
            + " updated_at = clock_timestamp() where id = ?",
        patient);

    DocumentContext doc = overview();

    assertThat(doc.read("$.facts.followedSince", String.class)).isEqualTo("2024-01-15");
    assertThat(doc.read("$.facts.age", Object.class)).isNull();
  }

  @Test
  void aMedicationThatEndedOrWhosePeriodIsOverIsNotActive() throws Exception {
    seed();

    DocumentContext doc = overview();

    List<String> ids = doc.read("$.facts.activeMedications[*].entryId");
    assertThat(ids).containsExactly(sertraline.toString());
    assertThat(ids).doesNotContain(lorazepam.toString(), zopiclone.toString());
  }

  @Test
  void aMedicationWithAnOpenPeriodOrAPrecisionBoundIsActiveWithoutAnEndMarker() throws Exception {
    // Période ouverte (period_from sans period_to) : en cours.
    UUID open = post(medicationPayload("escitalopram", daysAgo(10), today().minusDays(10), null));
    // Précision « mois » sans fin saisie : clinical pose occurred_to en fin de mois, ce n'est pas
    // une fin de traitement.
    Map<String, Object> monthly = medicationPayload("quetiapine", daysAgo(40));
    monthly.put("occurred", Map.of("from", daysAgo(40).toString(), "precision", "month"));
    UUID month = post(monthly);

    DocumentContext doc = overview("window", "12m");

    assertThat(doc.read("$.facts.activeMedications[*].entryId", List.class))
        .containsExactly(open.toString(), month.toString());
    assertThat(doc.read("$.facts.activeMedications[0].periodFrom", String.class))
        .isEqualTo(today().minusDays(10).toString());
    assertThat(doc.read("$.facts.activeMedications[0].periodTo", Object.class)).isNull();
    assertThat(doc.read("$.trend.markers[?(@.change=='start')].entryId", List.class))
        .containsExactlyInAnyOrder(open.toString(), month.toString());
    assertThat(doc.read("$.trend.markers[?(@.change=='end')]", List.class)).isEmpty();
  }

  @Test
  void activeMedicationsKeepOneLinePerMoleculeTheMostRecentFirst() throws Exception {
    UUID older = medication("Sertraline", "25 mg", daysAgo(90));
    UUID newer = medication("sertraline", "50 mg", daysAgo(15));
    UUID other = medication("zopiclone", "3,75 mg", daysAgo(30));

    DocumentContext doc = overview();

    assertThat(doc.read("$.facts.activeMedications[*].entryId", List.class))
        .containsExactly(newer.toString(), other.toString())
        .doesNotContain(older.toString());
    assertThat(doc.read("$.facts.activeMedications[0].dosage", String.class)).isEqualTo("50 mg");
  }

  // ---- situations actives

  @Test
  void activeSituationsAreOrderedByLinkCountWithLinkedFeelingStats() throws Exception {
    seed();

    DocumentContext doc = overview();

    assertThat(doc.read("$.activeSituations", List.class)).hasSize(2);
    assertThat(doc.read("$.activeSituations[0].entryId", String.class))
        .isEqualTo(licenciement.toString());
    assertThat(doc.read("$.activeSituations[0].title", String.class)).isEqualTo("Licenciement");
    assertThat(doc.read("$.activeSituations[0].since", String.class)).isNotBlank();
    assertThat(doc.read("$.activeSituations[0].linkCount", Integer.class)).isEqualTo(2);
    // Un lien dans chaque sens (la situation déclenche f1, f2 est causé par la situation).
    assertThat(doc.read("$.activeSituations[0].linkedFeelingCount", Integer.class)).isEqualTo(2);
    assertThat(doc.read("$.activeSituations[0].averageIntensity", Double.class)).isEqualTo(6.0);
    assertThat(doc.read("$.activeSituations[1].entryId", String.class))
        .isEqualTo(demenagement.toString());
    assertThat(doc.read("$.activeSituations[1].linkCount", Integer.class)).isZero();
    assertThat(doc.read("$.activeSituations[1].linkedFeelingCount", Integer.class)).isZero();
    assertThat(doc.read("$.activeSituations[1].averageIntensity", Object.class)).isNull();
  }

  @Test
  void anArchivedLinkedFeelingNoLongerCountsForTheSituation() throws Exception {
    seed();
    assertThat(overview().read("$.activeSituations[0].linkedFeelingCount", Integer.class))
        .isEqualTo(2);

    archive(f1);

    DocumentContext doc = overview();
    assertThat(doc.read("$.activeSituations[0].linkedFeelingCount", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.activeSituations[0].averageIntensity", Double.class)).isEqualTo(4.0);
  }

  // ---- brouillons

  @Test
  void draftsCountDraftEntriesAndExposeTheOpenSession() throws Exception {
    seed();

    DocumentContext doc = overview();

    assertThat(doc.read("$.drafts.count", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.drafts.openSession.entryId", String.class))
        .isEqualTo(session.toString());
    assertThat(doc.read("$.drafts.openSession.startedAt", String.class)).isNotBlank();
  }

  @Test
  void openSessionIsNullWhenEverySessionIsClosed() throws Exception {
    send(
            MockMvcRequestBuilders.post("/api/sessions/{id}/close", session),
            Map.of("durationMin", 45, "summary", "Fin"))
        .andExpect(status().isOk());

    DocumentContext doc = overview();

    assertThat(doc.read("$.drafts.openSession", Object.class)).isNull();
    assertThat(doc.read("$.drafts.count", Integer.class)).isZero();
    assertThat(doc.read("$.facts.sessionCount", Integer.class)).isEqualTo(1);
  }

  // ---- tendance et émotions

  @Test
  void trendHasAtMostNinetyPointsWithRollingAverageAndSessionMarkers() throws Exception {
    seed();

    DocumentContext doc = overview();

    List<String> dates = doc.read("$.trend.points[*].date");
    assertThat(dates).hasSize(90);
    assertThat(dates.size()).isLessThanOrEqualTo(90);
    assertThat(dates.get(89)).isEqualTo(today().toString());
    assertThat(dates.get(0)).isEqualTo(today().minusDays(89).toString());
    // Aujourd'hui : f1 (8, J-3) et le ressenti privé (2, J-1) ; l'archivé (10) et la phase de vie
    // (9) sont exclus.
    assertThat(doc.read("$.trend.points[89].averageIntensity", Double.class)).isEqualTo(5.0);
    // J-10 : f2 (4) seul dans les 7 jours.
    assertThat(doc.read("$.trend.points[79].averageIntensity", Double.class)).isEqualTo(4.0);
    // J-25 : rien dans les 7 jours précédents.
    assertThat(doc.read("$.trend.points[64].averageIntensity", Object.class)).isNull();
    assertThat(doc.read("$.trend.points[64].valence", Object.class)).isNull();
    List<String> sessionMarkers = doc.read("$.trend.markers[?(@.kind=='session')].entryId");
    assertThat(sessionMarkers).hasSize(2).contains(session.toString());
  }

  @Test
  void markersListMedicationStartsAndEnds() throws Exception {
    seed();

    DocumentContext doc = overview();

    List<Map<String, Object>> meds = doc.read("$.trend.markers[?(@.kind=='medication')]");
    assertThat(meds).hasSize(5);
    List<String> starts = doc.read("$.trend.markers[?(@.change=='start')].entryId");
    List<String> ends = doc.read("$.trend.markers[?(@.change=='end')].entryId");
    assertThat(starts)
        .containsExactlyInAnyOrder(
            sertraline.toString(), lorazepam.toString(), zopiclone.toString());
    assertThat(ends).containsExactlyInAnyOrder(lorazepam.toString(), zopiclone.toString());
    assertThat(doc.read("$.trend.markers[?(@.entryId=='" + sertraline + "')].label", List.class))
        .containsExactly("sertraline 50 mg");
    List<String> dates = doc.read("$.trend.markers[*].date");
    assertThat(dates).isSorted();
    // Une séance sans titre n'a pas de libellé figé en base : le client traduit sessionKind.
    assertThat(doc.read("$.trend.markers[?(@.kind=='session')].sessionKind", List.class))
        .containsOnly("session");
    assertThat(doc.read("$.trend.markers[?(@.kind=='session')].label", List.class))
        .containsOnlyNulls();
  }

  @Test
  void aFeelingWhosePeriodMiddleIsInTheFutureCountsAsToday() throws Exception {
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
    post(payload);

    DocumentContext doc = overview("window", "30d");

    assertThat(doc.read("$.emotions.top[*].code", List.class)).containsExactly("fear");
    assertThat(doc.read("$.trend.points[29].averageIntensity", Double.class)).isEqualTo(7.0);
    assertThat(doc.read("$.trend.points[28].averageIntensity", Object.class)).isNull();
  }

  @Test
  void emotionsRankTheWindowAndCompareWithThePreviousOne() throws Exception {
    seed();

    DocumentContext doc = overview();

    assertThat(doc.read("$.emotions.top[*].code", List.class))
        .containsExactly("anxiety", "joy", "sadness");
    assertThat(doc.read("$.emotions.top[0].count", Integer.class)).isEqualTo(2);
    assertThat(doc.read("$.emotions.top[0].previousCount", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.emotions.top[0].delta", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.emotions.top[0].label", String.class)).isNotBlank();
    assertThat(doc.read("$.emotions.top[1].previousCount", Integer.class)).isZero();
  }

  // ---- fenêtres

  @Test
  void thirtyDaysNinetyDaysAndTwelveMonthsChangeTheBounds() throws Exception {
    seed();

    DocumentContext d30 = overview("window", "30d");
    DocumentContext d90 = overview("window", "90d");
    DocumentContext m12 = overview("window", "12m");

    assertThat(d30.read("$.window", String.class)).isEqualTo("30d");
    assertThat(d30.read("$.trend.points", List.class)).hasSize(30);
    assertThat(d30.read("$.emotions.top[*].code", List.class))
        .containsExactly("anxiety", "sadness");
    assertThat(d30.read("$.emotions.top[0].delta", Integer.class)).isEqualTo(2);
    assertThat(d30.read("$.trend.markers[?(@.kind=='medication')]", List.class)).hasSize(1);

    assertThat(d90.read("$.window", String.class)).isEqualTo("90d");
    assertThat(d90.read("$.trend.points", List.class)).hasSize(90);

    assertThat(m12.read("$.window", String.class)).isEqualTo("12m");
    List<String> weekly = m12.read("$.trend.points[*].date");
    assertThat(weekly.size()).isBetween(52, 53);
    assertThat(weekly.get(weekly.size() - 1)).isEqualTo(today().toString());
    assertThat(
            LocalDate.parse(weekly.get(1)).toEpochDay()
                - LocalDate.parse(weekly.get(0)).toEpochDay())
        .isEqualTo(7);
    assertThat(m12.read("$.emotions.top[0].code", String.class)).isEqualTo("anxiety");
    assertThat(m12.read("$.emotions.top[0].count", Integer.class)).isEqualTo(3);

    // Les blocs indépendants de la fenêtre sont identiques.
    assertThat(d30.read("$.facts", Map.class)).isEqualTo(m12.read("$.facts", Map.class));
    assertThat(d30.read("$.activeSituations", List.class))
        .isEqualTo(d90.read("$.activeSituations", List.class));
  }

  @Test
  void theWindowDefaultsToNinetyDays() throws Exception {
    DocumentContext doc = overview();

    assertThat(doc.read("$.window", String.class)).isEqualTo("90d");
    assertThat(doc.read("$.trend.points", List.class)).hasSize(90);
  }

  @Test
  void readingAnotherWindowReadsTheStoredProjectionWithoutRecomputing() throws Exception {
    note("Une note", daysAgo(1));
    overview("window", "90d");
    String firstComputedAt =
        jdbc.queryForObject(
            "select computed_at::text from patient_overview where patient_id = ?",
            String.class,
            patient);

    overview("window", "30d");
    overview("window", "12m");

    assertThat(
            jdbc.queryForObject(
                "select computed_at::text from patient_overview where patient_id = ?",
                String.class,
                patient))
        .isEqualTo(firstComputedAt);
  }

  @Test
  void theStoredPayloadHoldsEveryWindowButNeverTheRecentActivity() throws Exception {
    note("Une note", daysAgo(1));
    overview();

    String stored = storedPayload(patient);

    assertThat(stored)
        .contains("\"windows\"", "\"30d\"", "\"90d\"", "\"12m\"", "\"facts\"", "\"drafts\"")
        .contains("\"activeSituations\"")
        .doesNotContain("recentActivity");
    assertThat(
            jdbc.queryForObject(
                "select source_rev from patient_overview where patient_id = ?",
                Long.class,
                patient))
        .isEqualTo(clinicalRev());
    assertThat(
            jdbc.queryForObject(
                "select compute_ms from patient_overview where patient_id = ?",
                Integer.class,
                patient))
        .isNotNull();
  }

  // ---- exclusions

  @Test
  void archivedEntriesAreExcludedAndPrivateEntriesAreIncluded() throws Exception {
    UUID visible = feeling("joy", 7, daysAgo(2), "private", null);
    UUID hidden = feeling("joy", 3, daysAgo(2));
    archive(hidden);

    DocumentContext doc = overview("window", "30d");

    assertThat(doc.read("$.emotions.top[0].code", String.class)).isEqualTo("joy");
    assertThat(doc.read("$.emotions.top[0].count", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.trend.points[29].averageIntensity", Double.class)).isEqualTo(7.0);
    assertThat(visible).isNotEqualTo(hidden);
  }

  @Test
  void lifePhaseEntriesAreLeftOutOfTheCurves() throws Exception {
    UUID id = feeling("anger", 9, daysAgo(2));
    jdbc.update("update entry set occurred_precision = 'life_phase' where id = ?", id);
    touchRev();

    DocumentContext doc = overview("window", "30d");

    assertThat(doc.read("$.emotions.top", List.class)).isEmpty();
    assertThat(doc.read("$.trend.points[29].averageIntensity", Object.class)).isNull();
  }

  // ---- erreurs

  @Test
  void anInvalidWindowIsAProblemJsonWithAFieldError() throws Exception {
    overviewRaw(patient, "window", "7d")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("OVERVIEW_BAD_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("window"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_WINDOW"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("Fenêtre invalide : valeurs admises 30d, 90d ou 12m."));
    overviewRaw(patient, "window", "90D").andExpect(status().isBadRequest());
    // Un paramètre vide vaut un paramètre absent : fenêtre par défaut.
    overviewRaw(patient, "window", "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.window").value("90d"));
  }

  @Test
  void theInvalidWindowMessageIsLocalized() throws Exception {
    mvc.perform(
            MockMvcRequestBuilders.get("/api/patients/{id}/overview", patient)
                .queryParam("window", "1y")
                .header("Accept-Language", "en")
                .with(auth))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_WINDOW"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("Invalid window: allowed values are 30d, 90d or 12m."));
  }

  @Test
  void anotherPractitionersPatientIsForbidden() throws Exception {
    UUID other = newPractitioner();
    UUID othersPatient = newPatient(other);

    overviewRaw(othersPatient)
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    assertThat(count("select count(*) from patient_overview where patient_id = ?", othersPatient))
        .isZero();
  }

  @Test
  void anUnknownPatientIsNotFound() throws Exception {
    overviewRaw(UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
  }

  @Test
  void anInvalidWindowOnAForeignPatientStaysForbidden() throws Exception {
    UUID othersPatient = newPatient(newPractitioner());

    overviewRaw(othersPatient, "window", "7d").andExpect(status().isForbidden());
  }

  // ---- activité récente

  @Test
  void recentActivityHoldsTenEntriesNewestFirstAndIncludesAFreshEntry() throws Exception {
    for (int i = 0; i < 12; i++) {
      note("Note numéro " + i, daysAgo(30 - i));
    }
    // Saisie une minute plus tard, mais datée d'il y a 200 jours : l'ordre est celui de la saisie.
    clock.advance(java.time.Duration.ofMinutes(1));
    UUID fresh = note("Tout juste saisie", daysAgo(200));

    DocumentContext doc = overview();

    List<String> ids = doc.read("$.recentActivity[*].entryId");
    assertThat(ids).hasSize(10);
    assertThat(ids.get(0)).isEqualTo(fresh.toString());
    assertThat(doc.read("$.recentActivity[0].type", String.class)).isEqualTo("note");
    assertThat(doc.read("$.recentActivity[0].status", String.class)).isEqualTo("final");
    assertThat(doc.read("$.recentActivity[0].visibility", String.class)).isEqualTo("clinical");
    assertThat(doc.read("$.recentActivity[0].sessionId", String.class))
        .isEqualTo(session.toString());
    assertThat(doc.read("$.recentActivity[0].version", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.recentActivity[0].occurredFrom", String.class)).isNotBlank();
    assertThat(doc.read("$.recentActivity[0].recordedAt", String.class)).isNotBlank();
  }

  @Test
  void recentActivityIsLiveAndSkipsArchivedEntries() throws Exception {
    UUID first = note("Première", daysAgo(3));
    overview();
    UUID second = note("Seconde", daysAgo(2));
    archive(first);

    List<String> ids = overview().read("$.recentActivity[*].entryId");

    assertThat(ids).contains(second.toString()).doesNotContain(first.toString());
  }

  @Test
  void aFreshReadReturnsTheComputedAtAndSourceRev() throws Exception {
    note("Une note", daysAgo(1));

    DocumentContext doc = overview();

    assertThat(doc.read("$.computedAt", String.class)).isNotBlank();
    assertThat(Instant.parse(doc.read("$.computedAt", String.class))).isEqualTo(NOW);
    assertThat(doc.read("$.sourceRev", Long.class)).isEqualTo(clinicalRev());
  }
}
