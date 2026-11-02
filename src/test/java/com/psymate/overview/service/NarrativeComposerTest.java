package com.psymate.overview.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.overview.dto.NarrativeDto;
import com.psymate.overview.model.NarrativeFacts;
import com.psymate.overview.model.NarrativeFacts.Dominant;
import com.psymate.overview.model.NarrativeFacts.FlagFact;
import com.psymate.overview.model.NarrativeFacts.MedicationFact;
import com.psymate.overview.model.NarrativeFacts.Trend;
import com.psymate.overview.model.NarrativeFacts.UncommentedEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Règles du résumé « En bref » : tendance et seuils, fenêtres vides, dominants, situations,
 * événement, traitement, drapeaux, locale EN et repli sur le français, vocabulaire interdit dans
 * les fragments des deux langues et dans toutes les phrases produites.
 */
class NarrativeComposerTest {

  private static final Locale FR = Locale.FRENCH;
  private static final Locale EN = Locale.ENGLISH;
  private static final LocalDate DAY = LocalDate.of(2026, 10, 24);
  private static final Dominant ANXIETY = new Dominant("anxiety", "anxiété", "anxiety", 6);

  /** Termes exclus (insensible à la casse et aux accents), FR et EN. */
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

  /** Toutes les phrases produites par les tests, vérifiées à la fin contre la liste interdite. */
  private static final List<String> PRODUCED = new ArrayList<>();

  private final NarrativeComposer composer = new NarrativeComposer();

  // ---- fabrique de faits

  private static NarrativeFacts facts(Trend trend, Dominant dominant) {
    return new NarrativeFacts(
        null, 0, null, trend, dominant, List.of(), null, List.of(), List.of());
  }

  private static NarrativeFacts empty() {
    return facts(null, null);
  }

  private NarrativeDto compose(NarrativeFacts facts, Locale locale) {
    NarrativeDto dto = composer.compose(facts, locale);
    PRODUCED.addAll(dto.sentences());
    if (dto.attention() != null) {
      PRODUCED.add(dto.attention());
    }
    assertNoForbidden(dto.sentences().toString() + dto.attention());
    return dto;
  }

  private String joined(NarrativeFacts facts, Locale locale) {
    return String.join(" ", compose(facts, locale).sentences());
  }

  private static String plain(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }

  private static void assertNoForbidden(String text) {
    String plain = plain(text);
    for (String word : FORBIDDEN) {
      assertThat(plain).as("terme interdit « %s » dans : %s", word, text).doesNotContain(word);
    }
  }

  // ---- tendance

  @ParameterizedTest(name = "{0} contre {1} : {2}")
  @CsvSource({
    "8.0, 3.0, monte",
    "3.0, 8.0, baisse",
    "5.0, 4.0, reste stable", // +1 exact : stable
    "4.0, 5.0, reste stable", // -1 exact : stable
    "5.1, 4.0, monte",
    "2.9, 4.0, baisse",
    "4.0, 4.0, reste stable",
    "7.5, 3.5, monte",
    "6.0, 6.9, reste stable"
  })
  void trendWordFollowsTheOnePointThreshold(double recent, double previous, String expected) {
    NarrativeFacts facts = facts(Trend.of(recent, previous, 12, 12), ANXIETY);
    String text = joined(facts, FR);
    assertThat(text).contains(expected);
    for (String other : List.of("monte", "baisse", "reste stable")) {
      if (!other.equals(expected)) {
        assertThat(text).doesNotContain(other);
      }
    }
    assertThat(text).contains("anxiété");
  }

  @Test
  void trendShowsBothAveragesWithFrenchDecimals() {
    String text = joined(facts(Trend.of(7.5, 3.5, 8, 8), ANXIETY), FR);
    assertThat(text).contains("7,5").contains("3,5");
  }

  @Test
  void emptyCurrentWindowGivesNotEnoughFeelings() {
    assertThat(joined(facts(Trend.of(null, 4.0, 0, 5), null), FR))
        .contains("Pas assez de ressentis pour dégager une tendance");
    assertThat(joined(facts(Trend.of(null, null, 0, 0), null), FR))
        .contains("Pas assez de ressentis pour dégager une tendance");
  }

  @Test
  void emptyPreviousWindowGivesTheAverageAloneWithoutTrendVerb() {
    String text = joined(facts(Trend.of(6.0, null, 5, 0), ANXIETY), FR);
    assertThat(text).contains("anxiété").contains("6");
    assertThat(text).doesNotContain("monte").doesNotContain("baisse").doesNotContain("stable");
    assertThat(text).doesNotContain("Pas assez");
  }

  @Test
  void noTrendDataOmitsTheSentence() {
    assertThat(compose(empty(), FR).sentences()).isEmpty();
  }

  // ---- ressenti dominant

  @Test
  void dominantUsesEnglishLabelOnlyForEnglishAndWhenPresent() {
    Dominant withEn = new Dominant("anger", "colère", "anger", 4);
    Dominant noEn = new Dominant("c_ab12cd34", "agacement", null, 4);
    Trend trend = Trend.of(6.0, 6.0, 4, 4);
    assertThat(joined(facts(trend, withEn), FR)).contains("colère");
    assertThat(joined(facts(trend, withEn), EN)).contains("anger").doesNotContain("colère");
    assertThat(joined(facts(trend, noEn), EN)).contains("agacement");
  }

  // ---- cadre

  @ParameterizedTest(name = "{0} séances, rythme {1} j : {2}")
  @CsvSource(
      nullValues = "null",
      value = {
        "12, 7, environ une séance par semaine",
        "12, 10, environ une séance par semaine",
        "12, 14, une séance toutes les 2 semaines",
        "12, 28, une séance toutes les 4 semaines",
        "12, 42, environ une séance par mois",
        "12, 61, une séance tous les 2 mois",
        "12, 91, une séance tous les 3 mois"
      })
  void frameDescribesTheRhythm(int sessions, int days, String expected) {
    NarrativeFacts facts =
        new NarrativeFacts(
            LocalDate.of(2026, 3, 3),
            sessions,
            days,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of());
    String text = joined(facts, FR);
    assertThat(text).startsWith("Suivi depuis le 3 mars 2026").contains("12 séances");
    assertThat(text).contains(expected);
  }

  @ParameterizedTest(name = "{0} séance(s) : {1}")
  @CsvSource({"0, aucune séance", "1, une séance", "2, 2 séances", "40, 40 séances"})
  void frameCountsSessionsWithPlurals(int sessions, String expected) {
    NarrativeFacts facts =
        new NarrativeFacts(
            LocalDate.of(2026, 3, 3),
            sessions,
            9,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of());
    String text = joined(facts, FR);
    assertThat(text).contains(expected);
    if (sessions == 0) {
      assertThat(text).doesNotContain("semaine");
    }
  }

  @Test
  void frameOmitsRhythmWithoutMedianAndIsOmittedWithoutStartDate() {
    NarrativeFacts noRhythm =
        new NarrativeFacts(
            LocalDate.of(2026, 3, 3), 2, null, null, null, List.of(), null, List.of(), List.of());
    assertThat(joined(noRhythm, FR)).isEqualTo("Suivi depuis le 3 mars 2026 : 2 séances.");
    NarrativeFacts noDate =
        new NarrativeFacts(null, 2, 7, null, null, List.of(), null, List.of(), List.of());
    assertThat(compose(noDate, FR).sentences()).isEmpty();
  }

  @Test
  void frameInEnglishUsesLongEnglishDate() {
    NarrativeFacts facts =
        new NarrativeFacts(
            LocalDate.of(2026, 3, 3), 1, null, null, null, List.of(), null, List.of(), List.of());
    assertThat(joined(facts, EN)).isEqualTo("Followed since March 3, 2026: one session.");
  }

  // ---- situations

  @ParameterizedTest(name = "{0} situation(s)")
  @ValueSource(ints = {0, 1, 2, 3, 4, 5})
  void situationsListUpToThreeThenCount(int n) {
    List<String> titles = new ArrayList<>();
    for (int i = 1; i <= n; i++) {
      titles.add("Situation " + i);
    }
    NarrativeFacts facts =
        new NarrativeFacts(null, 0, null, null, null, titles, null, List.of(), List.of());
    List<String> sentences = compose(facts, FR).sentences();
    switch (n) {
      case 0 -> assertThat(sentences).isEmpty();
      case 1 -> assertThat(sentences).containsExactly("Une situation reste ouverte : Situation 1.");
      case 2 ->
          assertThat(sentences)
              .containsExactly("Des situations restent ouvertes : Situation 1 et Situation 2.");
      case 3 ->
          assertThat(sentences)
              .containsExactly(
                  "Des situations restent ouvertes : Situation 1, Situation 2 et Situation 3.");
      case 4 ->
          assertThat(sentences)
              .containsExactly(
                  "Des situations restent ouvertes : Situation 1, Situation 2, Situation 3 et une"
                      + " autre.");
      default ->
          assertThat(sentences)
              .containsExactly(
                  "Des situations restent ouvertes : Situation 1, Situation 2, Situation 3 et "
                      + (n - 3)
                      + " autres.");
    }
  }

  @Test
  void situationsInEnglish() {
    NarrativeFacts facts =
        new NarrativeFacts(
            null,
            0,
            null,
            null,
            null,
            List.of("A", "B", "C", "D", "E"),
            null,
            List.of(),
            List.of());
    assertThat(compose(facts, EN).sentences())
        .containsExactly("Some situations are still open: A, B, C and 2 others.");
  }

  // ---- événement non commenté

  @Test
  void uncommentedEventNamesTitleAndDate() {
    NarrativeFacts facts =
        new NarrativeFacts(
            null,
            0,
            null,
            null,
            null,
            List.of(),
            new UncommentedEvent("Dispute avec le frère", DAY),
            List.of(),
            List.of());
    assertThat(compose(facts, FR).sentences())
        .singleElement()
        .asString()
        .contains("Dispute avec le frère")
        .contains("24 octobre 2026")
        .contains("pas encore été commenté");
  }

  @Test
  void uncommentedEventWithoutTitleStillGivesASentenceAndAbsentEventNone() {
    NarrativeFacts untitled =
        new NarrativeFacts(
            null,
            0,
            null,
            null,
            null,
            List.of(),
            new UncommentedEvent(" ", DAY),
            List.of(),
            List.of());
    assertThat(joined(untitled, FR))
        .contains("24 octobre 2026")
        .contains("pas encore été commenté");
    assertThat(compose(empty(), FR).sentences()).isEmpty();
  }

  // ---- traitement

  @Test
  void medicationListsMostRecentFirstWithItsStartDate() {
    NarrativeFacts one =
        new NarrativeFacts(
            null,
            0,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(new MedicationFact("sertraline", "50 mg", LocalDate.of(2026, 8, 30))),
            List.of());
    assertThat(compose(one, FR).sentences())
        .containsExactly("Traitement en cours : sertraline 50 mg, depuis le 30 août 2026.");

    NarrativeFacts two =
        new NarrativeFacts(
            null,
            0,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(
                new MedicationFact("lorazepam", null, LocalDate.of(2026, 10, 1)),
                new MedicationFact("sertraline", "50 mg", LocalDate.of(2026, 8, 30))),
            List.of());
    assertThat(compose(two, FR).sentences())
        .singleElement()
        .asString()
        .startsWith("Traitements en cours : lorazepam, sertraline 50 mg")
        .contains("1 octobre 2026");
    assertThat(compose(empty(), FR).sentences()).isEmpty();
  }

  // ---- point d'attention

  @ParameterizedTest(name = "drapeau {0}")
  @ValueSource(strings = {"watch", "risk", "shared_care"})
  void attentionPerFlagKind(String kind) {
    NarrativeFacts facts = withFlags(new FlagFact(kind, null, DAY));
    NarrativeDto dto = compose(facts, FR);
    assertThat(dto.sentences()).isEmpty();
    assertThat(dto.attention()).contains("24 octobre 2026");
    assertThat(plain(dto.attention())).doesNotContain("risque");
    NarrativeDto en = compose(facts, EN);
    assertThat(en.attention()).contains("October 24, 2026");
    assertThat(plain(en.attention())).doesNotContain("risk");
  }

  @Test
  void riskFlagUsesNeutralVigilanceWording() {
    NarrativeDto dto = compose(withFlags(new FlagFact("risk", "Sommeil très perturbé", DAY)), FR);
    assertThat(dto.attention())
        .isEqualTo(
            "Vigilance particulière demandée sur ce dossier depuis le 24 octobre 2026 (motif :"
                + " Sommeil très perturbé).");
  }

  @Test
  void severalFlagsAreJoinedBySpaceAndNoFlagGivesNullAttention() {
    NarrativeDto dto =
        compose(
            withFlags(
                new FlagFact("watch", "Rupture récente", DAY),
                new FlagFact("shared_care", null, LocalDate.of(2026, 9, 1))),
            FR);
    assertThat(dto.attention())
        .isEqualTo(
            "À surveiller depuis le 24 octobre 2026 (motif : Rupture récente). Suivi partagé avec"
                + " d'autres professionnels depuis le 1 septembre 2026.");
    assertThat(compose(empty(), FR).attention()).isNull();
    assertThat(compose(withFlags(new FlagFact("inconnu", null, DAY)), FR).attention()).isNull();
  }

  private static NarrativeFacts withFlags(FlagFact... flags) {
    return new NarrativeFacts(
        null, 0, null, null, null, List.of(), null, List.of(), List.of(flags));
  }

  // ---- locale

  @Test
  void englishLocaleGivesEnglishSentences() {
    String text = joined(facts(Trend.of(8.0, 3.0, 8, 8), ANXIETY), EN);
    assertThat(text).contains("going up").contains("anxiety").doesNotContain("monte");
    assertThat(joined(facts(Trend.of(3.0, 8.0, 8, 8), ANXIETY), EN)).contains("coming down");
    assertThat(joined(facts(Trend.of(5.0, 4.0, 8, 8), ANXIETY), EN)).contains("holding steady");
    assertThat(joined(facts(Trend.of(null, null, 0, 0), null), EN)).contains("Not enough feelings");
  }

  @Test
  void unsupportedOrMissingLocaleFallsBackToFrench() {
    NarrativeFacts facts = facts(Trend.of(8.0, 3.0, 8, 8), ANXIETY);
    assertThat(joined(facts, Locale.GERMAN)).contains("monte");
    assertThat(composer.compose(facts, null).sentences())
        .singleElement()
        .asString()
        .contains("monte");
  }

  @Test
  void missingEnglishFragmentFallsBackToFrenchForThatSentenceOnly() {
    ResourceBundleMessageSource real = new ResourceBundleMessageSource();
    real.setBasename("messages");
    real.setDefaultEncoding("UTF-8");
    real.setFallbackToSystemLocale(false);
    real.setAlwaysUseMessageFormat(true);
    MessageSource missingEnTrend =
        new MessageSource() {
          @Override
          public String getMessage(String code, Object[] args, String defaultMessage, Locale l) {
            if (code.equals("overview.narrative.trend.up") && l.getLanguage().equals("en")) {
              return defaultMessage;
            }
            return real.getMessage(code, args, defaultMessage, l);
          }

          @Override
          public String getMessage(String code, Object[] args, Locale l) {
            String m = getMessage(code, args, null, l);
            if (m == null) {
              throw new org.springframework.context.NoSuchMessageException(code, l);
            }
            return m;
          }

          @Override
          public String getMessage(
              org.springframework.context.MessageSourceResolvable r, Locale l) {
            return real.getMessage(r, l);
          }
        };
    NarrativeComposer partial = new NarrativeComposer(missingEnTrend);
    NarrativeFacts facts =
        new NarrativeFacts(
            LocalDate.of(2026, 3, 3),
            1,
            null,
            Trend.of(8.0, 3.0, 8, 8),
            ANXIETY,
            List.of(),
            null,
            List.of(),
            List.of());
    NarrativeDto dto = partial.compose(facts, EN);
    assertThat(dto.sentences()).hasSize(2);
    assertThat(dto.sentences().get(0)).isEqualTo("Followed since March 3, 2026: one session.");
    assertThat(dto.sentences().get(1)).contains("monte").contains("anxiété");
  }

  @Test
  void aCompletelyEmptyMessageSourceYieldsNoSentenceAndNoException() {
    MessageSource none =
        new MessageSource() {
          @Override
          public String getMessage(String code, Object[] args, String defaultMessage, Locale l) {
            return defaultMessage;
          }

          @Override
          public String getMessage(String code, Object[] args, Locale l) {
            throw new org.springframework.context.NoSuchMessageException(code, l);
          }

          @Override
          public String getMessage(
              org.springframework.context.MessageSourceResolvable r, Locale l) {
            throw new org.springframework.context.NoSuchMessageException("x", l);
          }
        };
    NarrativeFacts facts = withFlags(new FlagFact("watch", null, DAY));
    NarrativeDto dto = new NarrativeComposer(none).compose(facts, EN);
    assertThat(dto.sentences()).isEmpty();
    assertThat(dto.attention()).isNull();
  }

  @Test
  void nullFactsGiveAnEmptyNarrative() {
    NarrativeDto dto = composer.compose(null, FR);
    assertThat(dto.sentences()).isEmpty();
    assertThat(dto.attention()).isNull();
  }

  // ---- vocabulaire interdit

  private static Properties bundle(String file) throws IOException {
    Properties p = new Properties();
    try (InputStream in = NarrativeComposerTest.class.getResourceAsStream("/" + file)) {
      assertThat(in).as(file).isNotNull();
      p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
    return p;
  }

  private static Set<String> narrativeKeys(Properties p) {
    Set<String> keys = new TreeSet<>();
    for (String k : p.stringPropertyNames()) {
      if (k.startsWith("overview.narrative.")) {
        keys.add(k);
      }
    }
    return keys;
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"messages_fr.properties", "messages_en.properties"})
  void noForbiddenWordInAnyNarrativeFragment(String file) throws IOException {
    Properties p = bundle(file);
    Set<String> keys = narrativeKeys(p);
    assertThat(keys).isNotEmpty();
    for (String key : keys) {
      assertNoForbidden(p.getProperty(key));
    }
  }

  @Test
  void frenchAndEnglishBundlesHaveTheSameNarrativeKeys() throws IOException {
    assertThat(narrativeKeys(bundle("messages_en.properties")))
        .isEqualTo(narrativeKeys(bundle("messages_fr.properties")));
  }

  @Test
  void everyFragmentIsAValidMessageFormatPattern() throws IOException {
    for (String file : List.of("messages_fr.properties", "messages_en.properties")) {
      Properties p = bundle(file);
      for (String key : narrativeKeys(p)) {
        // lève IllegalArgumentException si le modèle est mal formé (apostrophe non doublée,
        // accolade)
        new java.text.MessageFormat(p.getProperty(key));
        assertThat(p.getProperty(key)).as(key).doesNotContainPattern(Pattern.compile("[^']'[^']"));
      }
    }
  }

  @Test
  void noForbiddenWordInAnySentenceProducedByTheTable() {
    // Rejoue, dans les deux langues, des scénarios variés : compose() contrôle chaque phrase.
    List<NarrativeFacts> scenarios =
        List.of(
            facts(Trend.of(8.0, 3.0, 8, 8), ANXIETY),
            facts(Trend.of(3.0, 8.0, 8, 8), ANXIETY),
            facts(Trend.of(5.0, 5.0, 8, 8), ANXIETY),
            facts(Trend.of(null, 5.0, 0, 8), null),
            facts(Trend.of(5.0, null, 8, 0), ANXIETY),
            new NarrativeFacts(
                LocalDate.of(2026, 1, 5),
                30,
                61,
                Trend.of(6.0, 3.0, 5, 5),
                ANXIETY,
                List.of("A", "B", "C", "D"),
                new UncommentedEvent("Deuil", DAY),
                List.of(new MedicationFact("sertraline", "50 mg", DAY)),
                List.of(
                    new FlagFact("watch", null, DAY),
                    new FlagFact("risk", null, DAY),
                    new FlagFact("shared_care", null, DAY))));
    for (NarrativeFacts scenario : scenarios) {
      compose(scenario, FR);
      compose(scenario, EN);
    }
    assertThat(PRODUCED).isNotEmpty();
    PRODUCED.forEach(NarrativeComposerTest::assertNoForbidden);
  }
}
