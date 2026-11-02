package com.psymate.clinical.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** GET /api/patients/{id}/timeline (M3-01) : fenêtre, curseur, facettes, filtres, erreurs. */
class TimelineIT extends TimelineWebSupport {

  @Autowired private PlatformTransactionManager transactions;

  /** Fenêtre de 2024 : exclut la séance ouverte par le test (maintenant). */
  private static final String Y2024 = "from=2024-01-01T00:00:00Z&to=2025-01-01T00:00:00Z";

  private ResultActions timeline(String query) throws Exception {
    return get(timelineUrl(query));
  }

  private List<String> itemIds(String query) throws Exception {
    return read(timeline(query).andExpect(status().isOk()), "$.items[*].id");
  }

  // ---- fenêtre, tri, éléments complets

  @Test
  void windowIsHalfOpenSortedDescendingWithCompleteItems() throws Exception {
    createTag("travail");
    createTag("Alpha");
    String feeling =
        feeling(
            "2024-03-10T19:00:00Z",
            "anxiety",
            8,
            "\"title\":\"Anxiété du soir\",\"tags\":[\"travail\",\"Alpha\"]");
    String event = event("2024-03-05T08:00:00Z", "Décès du père", null);
    String atFrom = note("2024-03-01T00:00:00Z");
    note("2024-02-29T23:59:59Z");
    note("2024-04-01T00:00:00Z");

    ResultActions result =
        timeline("from=2024-03-01T00:00:00Z&to=2024-04-01T00:00:00Z")
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.items", hasSize(3)))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()))
            .andExpect(jsonPath("$.items[0].id").value(feeling))
            .andExpect(jsonPath("$.items[1].id").value(event))
            .andExpect(jsonPath("$.items[2].id").value(atFrom))
            .andExpect(jsonPath("$.items[0].type").value("feeling"))
            .andExpect(jsonPath("$.items[0].occurred.from").value("2024-03-10T19:00:00Z"))
            .andExpect(jsonPath("$.items[0].occurred.to").value("2024-03-10T19:00:00Z"))
            .andExpect(jsonPath("$.items[0].occurred.precision").value("exact"))
            .andExpect(jsonPath("$.items[0].title").value("Anxiété du soir"))
            .andExpect(jsonPath("$.items[0].emotion.code").value("anxiety"))
            .andExpect(jsonPath("$.items[0].emotion.family").value("fear"))
            .andExpect(jsonPath("$.items[0].intensity").value(8))
            .andExpect(jsonPath("$.items[0].tags[0]").value("Alpha"))
            .andExpect(jsonPath("$.items[0].tags[1]").value("travail"))
            .andExpect(jsonPath("$.items[0].tags", hasSize(2)))
            .andExpect(jsonPath("$.items[0].linkCount").value(0))
            .andExpect(jsonPath("$.items[0].sessionId").value(session))
            .andExpect(jsonPath("$.items[0].status").value("final"))
            .andExpect(jsonPath("$.items[0].visibility").value("clinical"))
            .andExpect(jsonPath("$.items[0].authorType").value("practitioner"))
            .andExpect(jsonPath("$.items[0].recordedAt").exists())
            .andExpect(jsonPath("$.items[0].ongoing").value(false))
            .andExpect(jsonPath("$.items[1].title").value("Décès du père"))
            .andExpect(jsonPath("$.items[1].emotion").value(nullValue()))
            .andExpect(jsonPath("$.items[1].intensity").value(nullValue()))
            .andExpect(jsonPath("$.items[1].tags", hasSize(0)));
    assertThat(read(result, "$.items[*].authorType")).containsOnly("practitioner");
  }

  @Test
  void openWindowReturnsEverythingIncludingTheSession() throws Exception {
    String older = note("2020-01-01T00:00:00Z");

    List<String> ids = itemIds("");

    assertThat(ids).contains(session, older);
    assertThat(ids.indexOf(session)).isLessThan(ids.indexOf(older));
  }

  @Test
  void defaultLimitIsFiftyAndCursorIsOmittedOnTheLastPage() throws Exception {
    for (int i = 0; i < 3; i++) {
      note("2024-05-0" + (i + 1) + "T10:00:00Z");
    }
    timeline(Y2024)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(3)))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()));
  }

  // ---- pagination keyset

  @Test
  void keysetPaginationWalksEveryEntryOnceEvenWithIdenticalInstants() throws Exception {
    Set<String> expected = new LinkedHashSet<>();
    for (int i = 0; i < 7; i++) {
      expected.add(note("2024-06-15T12:00:00Z"));
    }
    for (int i = 0; i < 5; i++) {
      expected.add(feeling("2024-04-02T08:30:00Z", "anxiety", i));
    }
    for (int i = 0; i < 16; i++) {
      expected.add(note("2024-%02d-%02dT09:00:00Z".formatted(1 + i / 4, 10 + i % 4)));
    }
    assertThat(expected).hasSize(28);

    List<String> walked = new ArrayList<>();
    Instant previousFrom = null;
    String previousId = null;
    String cursor = null;
    int pages = 0;
    do {
      String query = Y2024 + "&limit=4" + (cursor == null ? "" : "&cursor=" + cursor);
      ResultActions page = timeline(query).andExpect(status().isOk());
      String json = body(page);
      List<String> ids = JsonPath.read(json, "$.items[*].id");
      List<String> froms = JsonPath.read(json, "$.items[*].occurred.from");
      assertThat(ids).hasSizeLessThanOrEqualTo(4);
      for (int i = 0; i < ids.size(); i++) {
        Instant from = Instant.parse(froms.get(i));
        if (previousFrom != null) {
          assertThat(from).isBeforeOrEqualTo(previousFrom);
          if (from.equals(previousFrom)) {
            assertThat(ids.get(i)).isLessThan(previousId);
          }
        }
        previousFrom = from;
        previousId = ids.get(i);
      }
      walked.addAll(ids);
      cursor = JsonPath.read(json, "$.nextCursor");
      pages++;
      assertThat(pages).as("garde-fou contre une boucle").isLessThanOrEqualTo(10);
    } while (cursor != null);

    assertThat(pages).isEqualTo(7);
    assertThat(walked).hasSize(28).doesNotHaveDuplicates();
    assertThat(new HashSet<>(walked)).isEqualTo(expected);
  }

  @Test
  void nextCursorIsNullWhenTheLastPageIsExactlyFull() throws Exception {
    for (int i = 0; i < 6; i++) {
      note("2024-07-0" + (i + 1) + "T10:00:00Z");
    }
    timeline(Y2024 + "&limit=6")
        .andExpect(jsonPath("$.items", hasSize(6)))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    timeline(Y2024 + "&limit=5")
        .andExpect(jsonPath("$.items", hasSize(5)))
        .andExpect(jsonPath("$.nextCursor").isNotEmpty());
  }

  @Test
  void cursorKeepsMicrosecondPrecisionOfTheStoredInstant() throws Exception {
    String a = note("2024-08-01T10:00:00Z");
    String b = note("2024-08-01T10:00:00Z");
    // Deux entrées d'une milliseconde d'écart mais de micro-secondes différentes en base.
    jdbc.update(
        "update entry set occurred_from = ?::timestamptz, occurred_to = null where id = ?::uuid",
        "2024-08-01 10:00:00.000300+00",
        a);
    jdbc.update(
        "update entry set occurred_from = ?::timestamptz, occurred_to = null where id = ?::uuid",
        "2024-08-01 10:00:00.000200+00",
        b);
    String c = note("2024-07-31T10:00:00Z");

    ResultActions first =
        timeline(Y2024 + "&limit=1").andExpect(jsonPath("$.items[0].id").value(a));
    String cursor = JsonPath.read(body(first), "$.nextCursor");
    ResultActions second =
        timeline(Y2024 + "&limit=1&cursor=" + cursor).andExpect(jsonPath("$.items[0].id").value(b));
    String cursor2 = JsonPath.read(body(second), "$.nextCursor");
    timeline(Y2024 + "&limit=1&cursor=" + cursor2).andExpect(jsonPath("$.items[0].id").value(c));
  }

  // ---- facettes

  @Test
  void facetsCountTheWholeWindowNotJustThePage() throws Exception {
    createTag("travail");
    createTag("sommeil");
    feeling("2024-03-01T10:00:00Z", "anxiety", 7, "\"tags\":[\"travail\"]");
    feeling("2024-03-02T10:00:00Z", "anxiety", 5, "\"tags\":[\"travail\",\"sommeil\"]");
    feeling("2024-03-03T10:00:00Z", "anxiety", 3);
    feeling("2024-03-04T10:00:00Z", "sadness", 6);
    note("2024-03-05T10:00:00Z");
    note("2024-03-06T10:00:00Z", "\"tags\":[\"sommeil\"]");
    event("2024-03-07T10:00:00Z", "Déménagement", null);
    situation("2024-03-08T10:00:00Z", null, "Surcharge", null);
    // Hors fenêtre : ne doivent rien changer aux comptes.
    feeling("2023-12-31T23:00:00Z", "anxiety", 9, "\"tags\":[\"travail\"]");
    note("2025-01-01T00:00:00Z");

    timeline(Y2024 + "&limit=2")
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.nextCursor").isNotEmpty())
        .andExpect(jsonPath("$.facets.types.feeling").value(4))
        .andExpect(jsonPath("$.facets.types.note").value(2))
        .andExpect(jsonPath("$.facets.types.event").value(1))
        .andExpect(jsonPath("$.facets.types.situation").value(1))
        .andExpect(jsonPath("$.facets.types.session").doesNotExist())
        .andExpect(jsonPath("$.facets.emotions.anxiety").value(3))
        .andExpect(jsonPath("$.facets.emotions.sadness").value(1))
        .andExpect(jsonPath("$.facets.emotions.length()").value(2))
        .andExpect(jsonPath("$.facets.tags.travail").value(2))
        .andExpect(jsonPath("$.facets.tags.sommeil").value(2))
        .andExpect(jsonPath("$.facets.tags.length()").value(2));
  }

  @Test
  void facetsDoNotDependOnTheCursor() throws Exception {
    for (int i = 0; i < 6; i++) {
      feeling("2024-03-0" + (i + 1) + "T10:00:00Z", "anxiety", i);
    }
    ResultActions first = timeline(Y2024 + "&limit=4").andExpect(status().isOk());
    String cursor = JsonPath.read(body(first), "$.nextCursor");
    timeline(Y2024 + "&limit=4&cursor=" + cursor)
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.nextCursor").value(nullValue()))
        .andExpect(jsonPath("$.facets.types.feeling").value(6))
        .andExpect(jsonPath("$.facets.emotions.anxiety").value(6));
  }

  @Test
  void facetsFollowTheTypesFilter() throws Exception {
    feeling("2024-03-01T10:00:00Z", "anxiety", 7);
    feeling("2024-03-02T10:00:00Z", "joy", 4);
    note("2024-03-03T10:00:00Z");
    event("2024-03-04T10:00:00Z", "Voyage", null);

    timeline(Y2024 + "&types=feeling")
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.facets.types.feeling").value(2))
        .andExpect(jsonPath("$.facets.types.length()").value(1))
        .andExpect(jsonPath("$.facets.emotions.anxiety").value(1))
        .andExpect(jsonPath("$.facets.emotions.joy").value(1));
  }

  // ---- filtres

  @Test
  void typesFilterAcceptsCommaSeparatedAndRepeatedValues() throws Exception {
    String n = note("2024-03-01T10:00:00Z");
    String e = event("2024-03-02T10:00:00Z", "Voyage", null);
    feeling("2024-03-03T10:00:00Z", "anxiety", 7);

    assertThat(itemIds(Y2024 + "&types=note,event")).containsExactly(e, n);
    assertThat(itemIds(Y2024 + "&types=note&types=event")).containsExactly(e, n);
    assertThat(itemIds(Y2024 + "&types=note")).containsExactly(n);
  }

  @Test
  void emotionsFilterKeepsOnlyMatchingFeelings() throws Exception {
    String a = feeling("2024-03-01T10:00:00Z", "anxiety", 7);
    String s = feeling("2024-03-02T10:00:00Z", "sadness", 4);
    feeling("2024-03-03T10:00:00Z", "joy", 4);
    note("2024-03-04T10:00:00Z");

    assertThat(itemIds(Y2024 + "&emotions=anxiety")).containsExactly(a);
    assertThat(itemIds(Y2024 + "&emotions=anxiety,sadness")).containsExactly(s, a);
    assertThat(itemIds(Y2024 + "&emotions=nope")).isEmpty();
  }

  @Test
  void tagsFilterResolvesLabelsAndUnknownLabelsMatchNothing() throws Exception {
    createTag("travail");
    createTag("sommeil");
    String t = note("2024-03-01T10:00:00Z", "\"tags\":[\"travail\"]");
    String s = feeling("2024-03-02T10:00:00Z", "anxiety", 7, "\"tags\":[\"sommeil\"]");
    note("2024-03-03T10:00:00Z");

    assertThat(itemIds(Y2024 + "&tags=travail")).containsExactly(t);
    assertThat(itemIds(Y2024 + "&tags=TRAVAIL")).containsExactly(t);
    assertThat(itemIds(Y2024 + "&tags=travail,sommeil")).containsExactly(s, t);
    assertThat(itemIds(Y2024 + "&tags=inconnu")).isEmpty();
    timeline(Y2024 + "&tags=inconnu")
        .andExpect(jsonPath("$.facets.types.length()").value(0))
        .andExpect(jsonPath("$.facets.tags.length()").value(0));
  }

  @Test
  void draftsAreIncludedByDefaultAndDraftsOnlyKeepsOnlyDrafts() throws Exception {
    String finalNote = note("2024-03-01T10:00:00Z");
    String draftNote = note("2024-03-02T10:00:00Z", "\"status\":\"draft\"");

    List<String> all = itemIds(Y2024);
    assertThat(all).containsExactly(draftNote, finalNote);
    assertThat(itemIds(Y2024 + "&draftsOnly=true")).containsExactly(draftNote);
    timeline(Y2024 + "&draftsOnly=true")
        .andExpect(jsonPath("$.items[0].status").value("draft"))
        .andExpect(jsonPath("$.facets.types.note").value(1));
  }

  // ---- situations en cours

  @Test
  void ongoingSituationsOverlappingTheWindowAreListedApart() throws Exception {
    String startedBeforeAndOngoing = situation("2023-11-01T00:00:00Z", null, "Surcharge", null);
    String endedBeforeWindow =
        situation("2023-11-01T00:00:00Z", "2023-12-01T00:00:00Z", "Deuil", null);
    String endsInsideWindow =
        situation("2023-12-01T00:00:00Z", "2024-03-05T00:00:00Z", "Chômage", null);
    String startsInside = situation("2024-03-10T00:00:00Z", null, "Nouveau poste", null);
    String startsAfterWindow = situation("2024-05-01T00:00:00Z", null, "Plus tard", null);
    String note = note("2024-03-12T10:00:00Z");

    ResultActions result =
        timeline("from=2024-03-01T00:00:00Z&to=2024-04-01T00:00:00Z").andExpect(status().isOk());

    assertThat(read(result, "$.items[*].id")).containsExactly(note, startsInside);
    assertThat(read(result, "$.ongoingSituations[*].id"))
        .containsExactly(startsInside, endsInsideWindow, startedBeforeAndOngoing)
        .doesNotContain(endedBeforeWindow, startsAfterWindow);
    result
        .andExpect(jsonPath("$.ongoingSituations[0].ongoing").value(true))
        .andExpect(jsonPath("$.ongoingSituations[1].ongoing").value(false))
        .andExpect(jsonPath("$.ongoingSituations[2].ongoing").value(true))
        .andExpect(jsonPath("$.ongoingSituations[2].title").value("Surcharge"))
        .andExpect(jsonPath("$.ongoingSituations[2].occurred.to").value(nullValue()))
        .andExpect(jsonPath("$.ongoingSituations[2].authorType").value("practitioner"))
        .andExpect(jsonPath("$.ongoingSituations[2].type").value("situation"));
  }

  @Test
  void ongoingSituationsIgnoreTypeFiltersButFollowDraftsAndVisibility() throws Exception {
    String finalSituation = situation("2023-11-01T00:00:00Z", null, "Surcharge", null);
    String draftSituation =
        situation("2023-12-01T00:00:00Z", null, "Brouillon", "\"status\":\"draft\"");
    String privateSituation =
        situation("2023-12-15T00:00:00Z", null, "Intime", "\"visibility\":\"private\"");
    feeling("2024-03-10T10:00:00Z", "anxiety", 6);
    String window = "from=2024-03-01T00:00:00Z&to=2024-04-01T00:00:00Z";

    ResultActions byType = timeline(window + "&types=feeling").andExpect(status().isOk());
    assertThat(read(byType, "$.ongoingSituations[*].id"))
        .containsExactly(draftSituation, finalSituation);
    byType.andExpect(jsonPath("$.items", hasSize(1)));

    assertThat(read(timeline(window + "&draftsOnly=true"), "$.ongoingSituations[*].id"))
        .containsExactly(draftSituation);
    assertThat(read(timeline(window + "&includePrivate=true"), "$.ongoingSituations[*].id"))
        .containsExactly(privateSituation, draftSituation, finalSituation);
  }

  @Test
  void aSituationWithoutAnyBoundAppearsWhenTheWindowIsOpen() throws Exception {
    String s = situation("2010-01-01T00:00:00Z", null, "Depuis longtemps", null);
    assertThat(read(timeline(""), "$.ongoingSituations[*].id")).contains(s);
  }

  // ---- archivées et privées

  @Test
  void archivedEntriesAreAbsentEverywhere() throws Exception {
    createTag("travail");
    String keep = feeling("2024-03-01T10:00:00Z", "anxiety", 7, "\"tags\":[\"travail\"]");
    String archived = feeling("2024-03-02T10:00:00Z", "anxiety", 9, "\"tags\":[\"travail\"]");
    String archivedSituation = situation("2023-11-01T00:00:00Z", null, "Archivée", null);
    delete("/api/entries/" + archived).andExpect(status().isNoContent());
    delete("/api/entries/" + archivedSituation).andExpect(status().isNoContent());

    ResultActions result =
        timeline("from=2024-03-01T00:00:00Z&to=2024-04-01T00:00:00Z&includePrivate=true")
            .andExpect(status().isOk());

    assertThat(read(result, "$.items[*].id")).containsExactly(keep);
    assertThat(read(result, "$.ongoingSituations[*].id")).isEmpty();
    result
        .andExpect(jsonPath("$.facets.types.feeling").value(1))
        .andExpect(jsonPath("$.facets.emotions.anxiety").value(1))
        .andExpect(jsonPath("$.facets.tags.travail").value(1));
  }

  @Test
  void privateEntriesNeedIncludePrivate() throws Exception {
    String visible = note("2024-03-01T10:00:00Z");
    String hidden = note("2024-03-02T10:00:00Z", "\"visibility\":\"private\"");

    assertThat(itemIds(Y2024)).containsExactly(visible);
    timeline(Y2024).andExpect(jsonPath("$.facets.types.note").value(1));
    assertThat(itemIds(Y2024 + "&includePrivate=false")).containsExactly(visible);
    assertThat(itemIds(Y2024 + "&includePrivate=true")).containsExactly(hidden, visible);
    timeline(Y2024 + "&includePrivate=true")
        .andExpect(jsonPath("$.items[0].visibility").value("private"))
        .andExpect(jsonPath("$.facets.types.note").value(2));
  }

  // ---- propriété

  @Test
  void anotherPractitionerGetsForbidden() throws Exception {
    note("2024-03-01T10:00:00Z");
    UUID other = fx.newPractitioner();

    send(MockMvcRequestBuilders.get(timelineUrl("")), null, as(other))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
  }

  @Test
  void ownershipIsCheckedBeforeValidation() throws Exception {
    UUID other = fx.newPractitioner();

    send(MockMvcRequestBuilders.get(timelineUrl("limit=0")), null, as(other))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
  }

  // ---- validation 422

  private void expectInvalid(String query, String field, String code) throws Exception {
    timeline(query)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value(field))
        .andExpect(jsonPath("$.errors[0].code").value(code))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
  }

  @Test
  void limitOutOfRangeIs422() throws Exception {
    expectInvalid("limit=201", "limit", "OUT_OF_RANGE");
    expectInvalid("limit=0", "limit", "OUT_OF_RANGE");
    expectInvalid("limit=-5", "limit", "OUT_OF_RANGE");
    timeline("limit=200").andExpect(status().isOk());
    timeline("limit=1").andExpect(status().isOk());
  }

  @Test
  void corruptedCursorIs422() throws Exception {
    expectInvalid("cursor=!!!", "cursor", "INVALID_VALUE");
    expectInvalid("cursor=aGVsbG8", "cursor", "INVALID_VALUE");
  }

  @Test
  void fromAfterToIs422() throws Exception {
    expectInvalid("from=2024-05-01T00:00:00Z&to=2024-04-01T00:00:00Z", "from", "INVALID_TIME");
  }

  @Test
  void unknownTypeIs422() throws Exception {
    expectInvalid("types=foo", "types", "INVALID_VALUE");
    expectInvalid("types=note,foo", "types", "INVALID_VALUE");
  }

  @Test
  void nonIsoDateIs422WithTheParameterName() throws Exception {
    expectInvalid("from=pas-une-date", "from", "INVALID_FORMAT");
    expectInvalid("to=2024-13-45", "to", "INVALID_FORMAT");
    expectInvalid("limit=beaucoup", "limit", "INVALID_FORMAT");
  }

  @Test
  void malformedPathIdIs400ForEveryClinicalController() throws Exception {
    for (String url :
        List.of(
            "/api/patients/pas-un-uuid/timeline",
            "/api/patients/pas-un-uuid/timeline/summary?granularity=month",
            "/api/entries/pas-un-uuid")) {
      get(url)
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
  }

  @Test
  void errorsAreAccumulatedByField() throws Exception {
    timeline("limit=0&cursor=!!!&types=foo&from=2024-05-01T00:00:00Z&to=2024-04-01T00:00:00Z")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors", hasSize(4)))
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(org.hamcrest.Matchers.hasItems("limit", "cursor", "types", "from")));
  }

  @Test
  void errorMessagesAreLocalized() throws Exception {
    timeline("limit=201")
        .andExpect(jsonPath("$.errors[0].message").value(containsString("entre 1 et 200")));
    send(MockMvcRequestBuilders.get(timelineUrl("limit=201")).header("Accept-Language", "en"), null)
        .andExpect(jsonPath("$.errors[0].message").value(containsString("between 1 and 200")));
  }

  // ---- performance indicative (jamais d'assertion sur le temps)

  @Test
  void twoThousandEntriesOverTenYearsPageAndSummarize() throws Exception {
    String t1 = createTag("perf1");
    String t2 = createTag("perf2");
    UUID tag1 = UUID.fromString(t1);
    UUID tag2 = UUID.fromString(t2);
    UUID sessionId = UUID.fromString(session);
    UUID tenant = fx.tenant;
    UUID owner = fx.practitioner;
    Instant base = Instant.parse("2010-01-01T00:00:00Z");
    String[] types = {"feeling", "note", "event", "feeling"};
    String[] emotions = {"anxiety", "sadness", "joy"};
    int total = 2000;

    List<Object[]> entries = new ArrayList<>();
    List<Object[]> versions = new ArrayList<>();
    List<Object[]> feelings = new ArrayList<>();
    List<Object[]> entryTags = new ArrayList<>();
    for (int i = 0; i < total; i++) {
      UUID id = UUID.randomUUID();
      UUID versionId = UUID.randomUUID();
      String type = i % 50 == 3 ? "situation" : types[i % 4];
      Timestamp at = Timestamp.from(base.plus(i * 2628L, ChronoUnit.MINUTES));
      entries.add(new Object[] {id, tenant, patient, type, at, sessionId, versionId, owner});
      versions.add(new Object[] {versionId, id, "Entrée " + i, owner});
      if (type.equals("feeling")) {
        feelings.add(new Object[] {id, emotions[i % 3], i % 11});
      }
      if (i % 3 == 0) {
        entryTags.add(new Object[] {id, i % 2 == 0 ? tag1 : tag2});
      }
    }
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              jdbc.batchUpdate(
                  "insert into entry (id, tenant_id, patient_id, type, occurred_from,"
                      + " session_id, current_version_id, created_by)"
                      + " values (?, ?, ?, ?, ?, ?, ?, ?)",
                  entries);
              jdbc.batchUpdate(
                  "insert into entry_version (id, entry_id, version, title, author_id)"
                      + " values (?, ?, 1, ?, ?)",
                  versions);
              jdbc.batchUpdate(
                  "insert into entry_feeling (entry_id, emotion_code, intensity)"
                      + " values (?, ?, ?)",
                  feelings);
              jdbc.batchUpdate("insert into entry_tag (entry_id, tag_id) values (?, ?)", entryTags);
            });
    jdbc.execute("analyze entry");
    assertThat(count("select count(*) from entry where patient_id = ?", patient))
        .isEqualTo(total + 1);

    String decade = "from=2010-01-01T00:00:00Z&to=2020-01-01T00:00:00Z";
    // Échauffement : plan, JIT, mappers.
    timeline(decade + "&limit=200").andExpect(status().isOk());
    get(summaryUrl("granularity=month&" + decade)).andExpect(status().isOk());

    long t0 = System.nanoTime();
    ResultActions page = timeline(decade + "&limit=200");
    long timelineMs = (System.nanoTime() - t0) / 1_000_000;
    page.andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(200)))
        .andExpect(jsonPath("$.nextCursor").isNotEmpty())
        .andExpect(jsonPath("$.facets.types.feeling").isNumber());

    long t1ns = System.nanoTime();
    ResultActions summary = get(summaryUrl("granularity=month&" + decade));
    long summaryMs = (System.nanoTime() - t1ns) / 1_000_000;
    summary.andExpect(status().isOk());
    List<String> periods = read(summary, "$.periods[*].period");
    assertThat(periods).isNotEmpty().hasSizeLessThanOrEqualTo(120).isSorted();

    System.out.println(
        "[M3-01 perf] timeline 200 items: "
            + timelineMs
            + " ms (cible 150), summary décennie: "
            + summaryMs
            + " ms (cible 100)");
  }
}
