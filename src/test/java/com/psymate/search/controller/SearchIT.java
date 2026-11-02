package com.psymate.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.psymate.search.SearchSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** GET /api/patients/{id}/search (M4-02) : filtres, classement, repli, facettes, erreurs. */
class SearchIT extends SearchSupport {

  private static final Instant NOW = Instant.now();

  private static Instant daysAgo(int days) {
    return NOW.minus(days, ChronoUnit.DAYS);
  }

  // ---- texte : exact et approché

  @Test
  void exactSearchHighlightsTheWordInTheSnippetAndTheTitle() throws Exception {
    UUID id =
        event(
            "Licenciement annoncé",
            "Le licenciement de son frère l'inquiète beaucoup depuis lundi",
            daysAgo(10));

    DocumentContext doc = search("q", "licenciement");

    assertThat(ids(doc)).containsExactly(id.toString());
    assertThat(doc.read("$.exactCount", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.approximateCount", Integer.class)).isZero();
    assertThat(doc.read("$.items[0].approximate", Boolean.class)).isFalse();
    assertThat(doc.read("$.items[0].snippet", String.class)).contains("<mark>licenciement</mark>");
    assertThat(doc.read("$.items[0].title", String.class)).contains("<mark>Licenciement</mark>");
    assertThat(doc.read("$.items[0].rank", Double.class)).isPositive();
    assertThat((Object) doc.read("$.items[0].similarity")).isNull();
    assertThat(doc.read("$.query.text", String.class)).isEqualTo("licenciement");
    assertThat(doc.read("$.tookMs", Long.class)).isGreaterThanOrEqualTo(0);
  }

  @Test
  void aTypoFindsTheEntryAsAnApproximateResult() throws Exception {
    UUID id = note("Le licenciement de son frère l'inquiète beaucoup depuis lundi", daysAgo(10));

    DocumentContext doc = search("q", "licencimen");

    assertThat(ids(doc)).containsExactly(id.toString());
    assertThat(doc.read("$.total", Long.class)).isZero();
    assertThat(doc.read("$.exactCount", Integer.class)).isZero();
    assertThat(doc.read("$.approximateCount", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.items[0].approximate", Boolean.class)).isTrue();
    assertThat(doc.read("$.items[0].similarity", Double.class)).isGreaterThan(0.3);
    assertThat((Object) doc.read("$.items[0].rank")).isNull();
    assertThat(doc.read("$.items[0].snippet", String.class)).contains("<mark>licenciement</mark>");
  }

  @Test
  void accentsAreIgnoredByTheExactSearch() throws Exception {
    UUID id = note("Une grande anxiété le soir, avant de dormir", daysAgo(5));

    DocumentContext doc = search("q", "anxiete");

    assertThat(ids(doc)).containsExactly(id.toString());
    assertThat(doc.read("$.items[0].approximate", Boolean.class)).isFalse();
    assertThat(doc.read("$.items[0].snippet", String.class)).contains("<mark>anxiété</mark>");
  }

  @Test
  void accentedWordsAreHighlightedWithTheirAccents() throws Exception {
    note("Une journée très éprouvante, avec des déménagements en série", daysAgo(5));

    assertThat(search("q", "journée").read("$.items[0].snippet", String.class))
        .contains("<mark>journée</mark>");
    assertThat(search("q", "journee").read("$.items[0].snippet", String.class))
        .contains("<mark>journée</mark>");
    assertThat(search("q", "déménagement").read("$.items[0].snippet", String.class))
        .contains("<mark>déménagements</mark>");
    assertThat(search("q", "éprouvante").read("$.items[0].snippet", String.class))
        .contains("<mark>éprouvante</mark>");
  }

  @Test
  void snippetsAreHtmlEscaped() throws Exception {
    note(
        "Le client écrit <script>alert(1)</script> puis x < y & z au sujet du licenciement",
        daysAgo(3));

    DocumentContext doc = search("q", "licenciement");

    String snippet = doc.read("$.items[0].snippet", String.class);
    assertThat(snippet).doesNotContain("<script>").contains("&lt;").contains("&amp;");
    assertThat(snippet).contains("<mark>licenciement</mark>");
  }

  @Test
  void exactResultsComeBeforeApproximateOnes() throws Exception {
    UUID exact = note("Il parle du licenciement de sa soeur", daysAgo(40));
    UUID approx = note("Il parle du licenciemant de sa mère", daysAgo(2));

    DocumentContext doc = search("q", "licenciement");

    assertThat(ids(doc)).containsExactly(exact.toString(), approx.toString());
    assertThat(doc.read("$.total", Long.class)).isEqualTo(1);
    assertThat(doc.read("$.exactCount", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.approximateCount", Integer.class)).isEqualTo(1);
    assertThat(doc.read("$.items[0].approximate", Boolean.class)).isFalse();
    assertThat(doc.read("$.items[1].approximate", Boolean.class)).isTrue();
  }

  @Test
  void approximateResultsAreNotAddedWhenThereAreEnoughExactOnes() throws Exception {
    for (int i = 0; i < 3; i++) {
      note("Séance " + i + " : le licenciement revient", daysAgo(10 + i));
    }
    note("Autre note sur un licenciemant", daysAgo(1));

    DocumentContext doc = search("q", "licenciement");

    assertThat(doc.read("$.total", Long.class)).isEqualTo(3);
    assertThat(doc.read("$.approximateCount", Integer.class)).isZero();
    assertThat(doc.read("$.items.length()", Integer.class)).isEqualTo(3);
  }

  @Test
  void recentEntriesAreBoostedByOnePointFive() throws Exception {
    UUID old = note("Épuisement professionnel et fatigue", daysAgo(200));
    UUID recent = note("Épuisement professionnel et fatigue", daysAgo(10));

    DocumentContext doc = search("q", "épuisement");

    assertThat(ids(doc)).containsExactly(recent.toString(), old.toString());
    double recentRank = doc.read("$.items[0].rank", Double.class);
    double oldRank = doc.read("$.items[1].rank", Double.class);
    assertThat(recentRank / oldRank).isCloseTo(1.5, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  void websearchOperatorsWork() throws Exception {
    UUID brother = note("Le licenciement de son frère", daysAgo(5));
    UUID sister = note("Le licenciement de sa soeur", daysAgo(4));
    UUID phrase = note("Un entretien manager difficile", daysAgo(3));
    note("Le manager a fait un entretien rapide", daysAgo(2));

    assertThat(ids(search("q", "licenciement -frère"))).containsExactly(sister.toString());
    assertThat(ids(search("q", "licenciement frère"))).contains(brother.toString());
    DocumentContext exactPhrase = search("q", "\"entretien manager\"");
    assertThat(exactPhrase.read("$.exactCount", Integer.class))
        .as("expression exacte")
        .isEqualTo(1);
    assertThat(ids(exactPhrase).get(0)).isEqualTo(phrase.toString());
  }

  @Test
  void emotionLabelAndTagsInTheVectorAreFoundByText() throws Exception {
    createTag("zen");
    // Tags et libellé d'émotion sont indexés par l'abonné EntryCreated (après le commit).
    UUID id = feeling("anxiety", "Une soirée comme les autres", daysAgo(2), "zen");

    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(ids(search("q", "anxiété"))).contains(id.toString());
              assertThat(ids(search("q", "zen"))).contains(id.toString());
            });
  }

  @Test
  void aTitleMatchOutranksABodyMatch() throws Exception {
    UUID body = note("Il évoque le déménagement de façon rapide", daysAgo(10));
    UUID title = event("Déménagement", "Rien d'autre à signaler ce jour-là", daysAgo(10));

    DocumentContext doc = search("q", "déménagement");

    assertThat(ids(doc)).containsExactly(title.toString(), body.toString());
  }

  // ---- syntaxe et filtres

  @Test
  void tagTokenFiltersOnTheTag() throws Exception {
    createTag("travail");
    createTag("famille");
    UUID tagged = note("Réunion difficile avec le manager", daysAgo(4), "travail");
    note("Dîner de famille tendu", daysAgo(3), "famille");
    note("Note sans étiquette", daysAgo(2));

    DocumentContext doc = search("q", "tag:travail");

    assertThat(ids(doc)).containsExactly(tagged.toString());
    assertThat(doc.read("$.query.tags", List.class)).containsExactly("travail");
    assertThat(doc.read("$.query.text", String.class)).isEmpty();
    assertThat(doc.read("$.items[0].tags", List.class)).containsExactly("travail");
    // casse et accents du libellé saisi sont sans effet
    assertThat(ids(search("q", "TAG:Travaïl"))).containsExactly(tagged.toString());
    assertThat(ids(search("q", "Tag:TRAVAIL"))).containsExactly(tagged.toString());
  }

  @Test
  void tagTokenCombinesWithFreeText() throws Exception {
    createTag("travail");
    UUID both = note("Réunion difficile avec le manager", daysAgo(4), "travail");
    note("Réunion de famille", daysAgo(3), "travail");
    note("Le manager a appelé", daysAgo(2));

    DocumentContext doc = search("q", "manager tag:travail");

    assertThat(ids(doc)).containsExactly(both.toString());
    assertThat(doc.read("$.query.text", String.class)).isEqualTo("manager");
  }

  @Test
  void quotedTagValueMayContainSpaces() throws Exception {
    createTag("vie pro");
    UUID id = note("Bilan de la semaine", daysAgo(4), "vie pro");
    note("Autre bilan", daysAgo(3));

    assertThat(ids(search("q", "tag:\"vie pro\""))).containsExactly(id.toString());
  }

  @Test
  void unknownTagOrEmotionReturnsNothingWithoutError() throws Exception {
    note("Une note", daysAgo(4));

    DocumentContext tag = search("q", "tag:inconnu");
    assertThat(tag.read("$.total", Long.class)).isZero();
    assertThat(tag.read("$.items.length()", Integer.class)).isZero();

    DocumentContext emotion = search("q", "émotion:inexistante");
    assertThat(emotion.read("$.total", Long.class)).isZero();
  }

  @Test
  void emotionTokenFiltersByLabelOrCode() throws Exception {
    UUID anxious = feeling("anxiety", "Monte avant les appels du soir", daysAgo(4));
    feeling("joy", "Belle journée au parc", daysAgo(3));
    note("Note ordinaire", daysAgo(2));

    for (String q :
        List.of("émotion:anxiété", "emotion:anxiety", "Émotion:ANXIÉTÉ", "emotion:anxiete")) {
      DocumentContext doc = search("q", q);
      assertThat(ids(doc)).as(q).containsExactly(anxious.toString());
    }
    DocumentContext doc = search("q", "emotion:anxiety");
    assertThat(doc.read("$.items[0].emotionCode", String.class)).isEqualTo("anxiety");
    assertThat(doc.read("$.query.emotions", List.class)).containsExactly("anxiety");
  }

  @Test
  void beforeAndAfterTokensFilterOnTheOccurredDate() throws Exception {
    UUID march = note("Note de mars", Instant.parse("2026-03-10T10:00:00Z"));
    UUID june = note("Note de juin", Instant.parse("2026-06-20T10:00:00Z"));
    UUID september = note("Note de septembre", Instant.parse("2026-09-05T10:00:00Z"));

    assertThat(ids(search("q", "avant:2026-06", "types", "note")))
        .containsExactly(march.toString());
    assertThat(ids(search("q", "before:2026-06-20", "types", "note")))
        .containsExactly(march.toString());
    assertThat(ids(search("q", "après:2026-06", "types", "note")))
        .containsExactly(september.toString());
    assertThat(ids(search("q", "apres:2026-06-19", "types", "note")))
        .containsExactlyInAnyOrder(june.toString(), september.toString());
    assertThat(ids(search("q", "après:2026-06-20", "types", "note")))
        .containsExactly(september.toString());
    assertThat(ids(search("q", "après:2026-03 avant:2026-09", "types", "note")))
        .containsExactly(june.toString());
    assertThat(ids(search("q", "avant:2027", "types", "note"))).hasSize(3);

    DocumentContext doc = search("q", "après:2026-06 avant:2026-12");
    assertThat(doc.read("$.query.after", String.class)).isEqualTo("2026-07-01T00:00:00Z");
    assertThat(doc.read("$.query.before", String.class)).isEqualTo("2026-12-01T00:00:00Z");
  }

  @Test
  void typesEmotionsTagsFromToAndSessionIdParametersFilter() throws Exception {
    createTag("travail");
    UUID firstSession = session;
    UUID anxiety = feeling("anxiety", "Anxieuse", Instant.parse("2026-03-10T10:00:00Z"), "travail");
    UUID joy = feeling("joy", "Contente", Instant.parse("2026-04-10T10:00:00Z"));
    UUID noteInFirst = note("Note de mars", Instant.parse("2026-03-12T10:00:00Z"));
    UUID secondSession = nextSession();
    UUID noteInSecond = note("Note de mai", Instant.parse("2026-05-12T10:00:00Z"), "travail");

    assertThat(ids(search("types", "feeling")))
        .containsExactlyInAnyOrder(anxiety.toString(), joy.toString());
    assertThat(ids(search("types", "note", "types", "feeling"))).hasSize(4);
    assertThat(ids(search("emotions", "joy"))).containsExactly(joy.toString());
    assertThat(ids(search("emotions", "anxiété"))).containsExactly(anxiety.toString());
    assertThat(ids(search("tags", "Travail")))
        .containsExactlyInAnyOrder(anxiety.toString(), noteInSecond.toString());
    assertThat(ids(search("tags", "#travail", "types", "note")))
        .containsExactly(noteInSecond.toString());
    assertThat(ids(search("from", "2026-04-01", "to", "2026-04-30", "types", "feeling")))
        .containsExactly(joy.toString());
    assertThat(ids(search("from", "2026-03-11T00:00:00Z", "to", "2026-03-12T10:00:00Z")))
        .containsExactly(noteInFirst.toString());
    assertThat(ids(search("to", "2026-03-10"))).containsExactly(anxiety.toString());
    assertThat(ids(search("sessionId", secondSession.toString(), "types", "note")))
        .containsExactly(noteInSecond.toString());
    assertThat(ids(search("sessionId", firstSession.toString(), "types", "note")))
        .containsExactly(noteInFirst.toString());
  }

  @Test
  void syntaxAndParametersAccumulate() throws Exception {
    createTag("travail");
    createTag("famille");
    UUID work = note("Note A", daysAgo(5), "travail");
    UUID family = note("Note B", daysAgo(4), "famille");
    note("Note C", daysAgo(3));

    assertThat(ids(search("q", "tag:travail", "tags", "famille")))
        .containsExactlyInAnyOrder(work.toString(), family.toString());
    // dates : intersection de la syntaxe et des paramètres
    assertThat(
            ids(
                search(
                    "q",
                    "avant:2100",
                    "from",
                    daysAgo(4).minus(1, ChronoUnit.HOURS).toString(),
                    "types",
                    "note")))
        .hasSize(2);
  }

  @Test
  void archivedEntriesAreExcludedUnlessRequested() throws Exception {
    UUID kept = note("Note gardée sur le sommeil", daysAgo(5));
    UUID archived = note("Note archivée sur le sommeil", daysAgo(4));
    archive(archived);

    assertThat(ids(search("q", "sommeil"))).containsExactly(kept.toString());
    assertThat(ids(search("q", "sommeil", "includeArchived", "false")))
        .containsExactly(kept.toString());

    DocumentContext doc = search("q", "sommeil", "includeArchived", "true");
    assertThat(ids(doc)).containsExactlyInAnyOrder(kept.toString(), archived.toString());
    List<String> archivedAt = doc.read("$.items[?(@.entryId=='" + archived + "')].archivedAt");
    assertThat(archivedAt).hasSize(1).doesNotContainNull();
  }

  // ---- liste sans texte

  @Test
  void emptyQueryListsByRecencyWithAPlainSnippet() throws Exception {
    UUID older = note("Plus ancienne", daysAgo(20));
    UUID newest = note("Plus récente <b>", daysAgo(1));
    UUID middle = note("Au milieu", daysAgo(10));

    DocumentContext doc = search("types", "note");

    assertThat(ids(doc)).containsExactly(newest.toString(), middle.toString(), older.toString());
    assertThat(doc.read("$.total", Long.class)).isEqualTo(3);
    assertThat(doc.read("$.items[0].snippet", String.class)).isEqualTo("Plus récente &lt;b&gt;");
    assertThat((Object) doc.read("$.items[0].rank")).isNull();
    assertThat(doc.read("$.approximateCount", Integer.class)).isZero();
    assertThat(ids(search("q", "   ", "types", "note"))).hasSize(3);
  }

  @Test
  void aLongBodyIsTruncatedInThePlainSnippet() throws Exception {
    note("mot ".repeat(200), daysAgo(1));

    DocumentContext doc = search("types", "note");

    assertThat(doc.read("$.items[0].snippet", String.class).length()).isLessThanOrEqualTo(201);
    assertThat(doc.read("$.items[0].snippet", String.class)).endsWith("…");
  }

  // ---- pagination

  @Test
  void resultsArePagedByTwenty() throws Exception {
    for (int i = 0; i < 25; i++) {
      sqlNote(me, "clinical", null, "Suivi de routine numéro " + i, daysAgo(i + 1));
    }

    DocumentContext page1 = search("q", "routine");
    DocumentContext page2 = search("q", "routine", "page", "2");
    DocumentContext page3 = search("q", "routine", "page", "3");

    assertThat(page1.read("$.total", Long.class)).isEqualTo(25);
    assertThat(page1.read("$.pageSize", Integer.class)).isEqualTo(20);
    assertThat(page1.read("$.page", Integer.class)).isEqualTo(1);
    assertThat(page1.read("$.items.length()", Integer.class)).isEqualTo(20);
    assertThat(page2.read("$.page", Integer.class)).isEqualTo(2);
    assertThat(page2.read("$.items.length()", Integer.class)).isEqualTo(5);
    assertThat(page2.read("$.total", Long.class)).isEqualTo(25);
    assertThat(page3.read("$.items.length()", Integer.class)).isZero();
    List<String> all = new java.util.ArrayList<>(ids(page1));
    all.addAll(ids(page2));
    assertThat(all).doesNotHaveDuplicates().hasSize(25);

    DocumentContext list = search("types", "note", "page", "2");
    assertThat(list.read("$.total", Long.class)).isEqualTo(25);
    assertThat(list.read("$.items.length()", Integer.class)).isEqualTo(5);
  }

  // ---- facettes

  @Test
  void facetsCountTheMatchesAndEachIgnoresItsOwnFilter() throws Exception {
    createTag("travail");
    createTag("famille");
    feeling("anxiety", "Fatigue le soir", daysAgo(5), "travail");
    feeling("anxiety", "Fatigue au réveil", daysAgo(4), "travail", "famille");
    feeling("joy", "Fatigue mais contente", daysAgo(3));
    note("Fatigue chronique", daysAgo(2), "famille");
    event("Arrêt maladie", "Fatigue et épuisement", daysAgo(1));
    note("Sans rapport avec le sujet", daysAgo(1));

    DocumentContext all = search("q", "fatigue");

    assertThat(all.read("$.total", Long.class)).isEqualTo(5);
    assertThat(all.read("$.facets.types[*].code", List.class))
        .containsExactly("feeling", "event", "note");
    assertThat(all.read("$.facets.types[*].count", List.class)).containsExactly(3, 1, 1);
    assertThat(all.read("$.facets.emotions[*].code", List.class)).containsExactly("anxiety", "joy");
    assertThat(all.read("$.facets.emotions[*].count", List.class)).containsExactly(2, 1);
    assertThat(all.read("$.facets.emotions[0].label", String.class)).isEqualTo("Anxiété");
    assertThat(all.read("$.facets.tags[*].label", List.class))
        .containsExactly("famille", "travail");
    assertThat(all.read("$.facets.tags[*].count", List.class)).containsExactly(2, 2);

    // Filtre « types=note » : la liste se réduit, la facette des types garde tous ses compteurs.
    DocumentContext notes = search("q", "fatigue", "types", "note");
    assertThat(notes.read("$.total", Long.class)).isEqualTo(1);
    assertThat(notes.read("$.facets.types[*].code", List.class))
        .containsExactly("feeling", "event", "note");
    assertThat(notes.read("$.facets.types[*].count", List.class)).containsExactly(3, 1, 1);
    // les autres facettes suivent le filtre de types
    assertThat(notes.read("$.facets.emotions.length()", Integer.class)).isZero();
    assertThat(notes.read("$.facets.tags[*].label", List.class)).containsExactly("famille");

    // Filtre « tags=travail » : la facette des tags garde « famille » ; les types suivent le tag.
    DocumentContext tagged = search("q", "fatigue", "tags", "travail");
    assertThat(tagged.read("$.total", Long.class)).isEqualTo(2);
    assertThat(tagged.read("$.facets.tags[*].label", List.class))
        .containsExactly("famille", "travail");
    assertThat(tagged.read("$.facets.types[*].code", List.class)).containsExactly("feeling");

    // Filtre « emotions=joy » : la facette des émotions garde « anxiety ».
    DocumentContext joy = search("q", "fatigue", "emotions", "joy");
    assertThat(joy.read("$.total", Long.class)).isEqualTo(1);
    assertThat(joy.read("$.facets.emotions[*].code", List.class)).containsExactly("anxiety", "joy");
  }

  @Test
  void facetsAlsoWorkWithoutText() throws Exception {
    note("Une note", daysAgo(3));
    note("Une autre note", daysAgo(2));
    feeling("joy", "Contente", daysAgo(1));

    DocumentContext doc = search();

    // la séance ouverte fait partie du dossier
    assertThat(doc.read("$.facets.types[*].code", List.class))
        .containsExactly("note", "feeling", "session");
    assertThat(doc.read("$.facets.types[*].count", List.class)).containsExactly(2, 1, 1);
  }

  // ---- visibilité

  @Test
  void privateEntriesOfAnotherPractitionerAreNeverReturned() throws Exception {
    UUID other = newPractitioner();
    UUID otherPrivate =
        sqlNote(other, "private", "Secret", "Confidence privée sur le deuil", daysAgo(5));
    UUID otherClinical =
        sqlNote(other, "clinical", null, "Observation partagée sur le deuil", daysAgo(4));
    UUID minePrivate =
        entry("note", null, "Ma note privée sur le deuil", daysAgo(3), "private", null, null);

    // lecture du vecteur par l'API après un rafraîchissement explicite
    jdbc.execute("select entry_search_vector_refresh('" + otherPrivate + "')");

    for (DocumentContext doc :
        List.of(search("q", "deuil"), search("q", "deuil", "includeArchived", "true"))) {
      assertThat(ids(doc))
          .containsExactlyInAnyOrder(otherClinical.toString(), minePrivate.toString())
          .doesNotContain(otherPrivate.toString());
      assertThat(doc.read("$.total", Long.class)).isEqualTo(2);
    }
    assertThat(ids(search("types", "note"))).doesNotContain(otherPrivate.toString());
    // même l'approché ne la renvoie pas
    assertThat(ids(search("q", "decuil"))).doesNotContain(otherPrivate.toString());
    // ni dans les compteurs
    DocumentContext facets = search("types", "note");
    assertThat(facets.read("$.facets.types[?(@.code=='note')].count", List.class))
        .containsExactly(2);
  }

  // ---- erreurs

  @Test
  void aQueryOf201CharactersIsRejected() throws Exception {
    searchRaw(patient, "q", "a".repeat(201))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("SEARCH_BAD_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("q"))
        .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("200")));
    searchRaw(patient, "q", "a".repeat(200)).andExpect(status().isOk());
  }

  @Test
  void pageBelowOneIsRejected() throws Exception {
    searchRaw(patient, "page", "0")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errors[0].field").value("page"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_PAGE"));
  }

  @Test
  void pageAboveTheCapIsRejectedInsteadOfOverflowing() throws Exception {
    searchRaw(patient, "page", "500").andExpect(status().isOk());
    searchRaw(patient, "page", "501")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("page"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_PAGE"));
    searchRaw(patient, "page", String.valueOf(Integer.MAX_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_PAGE"));
    searchRaw(patient, "from", "+999999999-01-01T00:00:00Z")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("from"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_DATE"));
  }

  @Test
  void malformedParametersAreReportedByField() throws Exception {
    searchRaw(patient, "page", "abc")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("page"))
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    searchRaw(patient, "sessionId", "pas-un-uuid")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errors[0].field").value("sessionId"));
    searchRaw(patient, "includeArchived", "peut-etre")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("includeArchived"));
  }

  @Test
  void invalidDatesUnknownTypesAndInvertedRangesAreReportedTogether() throws Exception {
    searchRaw(patient, "from", "hier", "types", "inconnu", "q", "avant:2026-13")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(jsonPath("$.errors[?(@.field=='from')].code").value("INVALID_DATE"))
        .andExpect(jsonPath("$.errors[?(@.field=='types')].code").value("UNKNOWN_TYPE"))
        .andExpect(jsonPath("$.errors[?(@.field=='q')].code").value("INVALID_DATE"));
    searchRaw(patient, "from", "2026-05-01", "to", "2026-04-01")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("to"))
        .andExpect(jsonPath("$.errors[0].code").value("RANGE_INVERTED"));
    // une même journée n'est pas une période inversée
    searchRaw(patient, "from", "2026-05-01", "to", "2026-05-01").andExpect(status().isOk());
  }

  @Test
  void errorMessagesAreInFrenchByDefaultAndInEnglishOnRequest() throws Exception {
    searchRaw(patient, "page", "0")
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("Le numéro de page doit être compris entre 1 et 500."));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/patients/{id}/search", patient)
                .queryParam("page", "0")
                .header("Accept-Language", "en")
                .with(auth))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[0].message").value("The page number must be between 1 and 500."));
  }

  @Test
  void anotherPractitionersPatientIsForbidden() throws Exception {
    UUID colleague = newPractitioner();
    UUID colleaguePatient = newPatient(colleague);

    searchRaw(colleaguePatient, "q", "test")
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
  }

  @Test
  void anUnknownPatientIsNotFound() throws Exception {
    ResultActions result = searchRaw(UUID.randomUUID(), "q", "test");
    result
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
  }

  @Test
  void aSearchNeverCrossesPatients() throws Exception {
    UUID otherPatient = newPatient(me);
    sqlNoteFor(otherPatient, "Le même mot : licenciement");
    UUID mine = note("Le même mot : licenciement", daysAgo(2));

    assertThat(ids(search("q", "licenciement"))).containsExactly(mine.toString());
  }

  /** Entrée d'un autre patient du même praticien (séance du patient courant détournée). */
  private void sqlNoteFor(UUID otherPatient, String body) {
    UUID otherSession = UUID.randomUUID();
    UUID entryId = UUID.randomUUID();
    UUID versionId = UUID.randomUUID();
    UUID sessionVersion = UUID.randomUUID();
    tx.executeWithoutResult(
        s -> {
          jdbc.update(
              "insert into entry (id, tenant_id, patient_id, type, occurred_from, occurred_to,"
                  + " current_version_id, created_by) values (?, ?, ?, 'session', now(), now(),"
                  + " ?, ?)",
              otherSession,
              tenant,
              otherPatient,
              sessionVersion,
              me);
          jdbc.update(
              "insert into entry_version (id, entry_id, version, body, author_id)"
                  + " values (?, ?, 1, null, ?)",
              sessionVersion,
              otherSession,
              me);
          jdbc.update(
              "insert into entry (id, tenant_id, patient_id, type, occurred_from, occurred_to,"
                  + " session_id, current_version_id, created_by)"
                  + " values (?, ?, ?, 'note', now(), now(), ?, ?, ?)",
              entryId,
              tenant,
              otherPatient,
              otherSession,
              versionId,
              me);
          jdbc.update(
              "insert into entry_version (id, entry_id, version, body, author_id)"
                  + " values (?, ?, 1, ?, ?)",
              versionId,
              entryId,
              body,
              me);
        });
  }
}
