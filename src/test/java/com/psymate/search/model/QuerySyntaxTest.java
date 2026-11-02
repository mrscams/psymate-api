package com.psymate.search.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.search.model.QuerySyntax.Parsed;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuerySyntaxTest {

  @Test
  void plainTextIsUnchanged() {
    Parsed p = QuerySyntax.parse("entretien manager");
    assertThat(p.text()).isEqualTo("entretien manager");
    assertThat(p.tags()).isEmpty();
    assertThat(p.emotions()).isEmpty();
    assertThat(p.before()).isNull();
    assertThat(p.after()).isNull();
    assertThat(p.errors()).isEmpty();
  }

  @Test
  void nullAndBlankGiveEmptyText() {
    assertThat(QuerySyntax.parse(null).text()).isEmpty();
    assertThat(QuerySyntax.parse("   ").text()).isEmpty();
  }

  @Test
  void tagTokenIsRemovedFromTheText() {
    Parsed p = QuerySyntax.parse("licenciement tag:travail");
    assertThat(p.text()).isEqualTo("licenciement");
    assertThat(p.tags()).containsExactly("travail");
  }

  @Test
  void tokensCanComeFirstOrInTheMiddleAndTheTextIsCleaned() {
    Parsed p = QuerySyntax.parse("  tag:travail   entretien   tag:famille  manager ");
    assertThat(p.text()).isEqualTo("entretien manager");
    assertThat(p.tags()).containsExactly("travail", "famille");
  }

  @Test
  void emotionTokenInFrenchAndEnglishWithOrWithoutAccents() {
    assertThat(QuerySyntax.parse("émotion:anxiété").emotions()).containsExactly("anxiété");
    assertThat(QuerySyntax.parse("emotion:anxiety").emotions()).containsExactly("anxiety");
    assertThat(QuerySyntax.parse("Emotion:peur").emotions()).containsExactly("peur");
    assertThat(QuerySyntax.parse("ÉMOTION:peur").emotions()).containsExactly("peur");
  }

  @Test
  void prefixIsCaseAndAccentInsensitive() {
    assertThat(QuerySyntax.parse("TAG:travail").tags()).containsExactly("travail");
    assertThat(QuerySyntax.parse("Tag:travail").tags()).containsExactly("travail");
    assertThat(QuerySyntax.parse("APRÈS:2026-06").after()).isNotNull();
    assertThat(QuerySyntax.parse("apres:2026-06").after()).isNotNull();
    assertThat(QuerySyntax.parse("Après:2026-06").after()).isNotNull();
    assertThat(QuerySyntax.parse("Avant:2026-06").before()).isNotNull();
  }

  @Test
  void quotedValueMayContainSpaces() {
    Parsed p = QuerySyntax.parse("tag:\"vie pro\" fatigue");
    assertThat(p.tags()).containsExactly("vie pro");
    assertThat(p.text()).isEqualTo("fatigue");
  }

  @Test
  void quotedPhraseInTheTextIsKeptAsIs() {
    Parsed p = QuerySyntax.parse("\"entretien manager\" tag:travail");
    assertThat(p.text()).isEqualTo("\"entretien manager\"");
    assertThat(p.tags()).containsExactly("travail");
  }

  @Test
  void aTokenInsideAQuotedPhraseIsNotAToken() {
    Parsed p = QuerySyntax.parse("\"voir tag:travail demain\"");
    assertThat(p.tags()).isEmpty();
    assertThat(p.text()).isEqualTo("\"voir tag:travail demain\"");
  }

  @Test
  void unknownPrefixStaysInTheText() {
    Parsed p = QuerySyntax.parse("séance:17oct heure:9");
    assertThat(p.text()).isEqualTo("séance:17oct heure:9");
    assertThat(p.errors()).isEmpty();
  }

  @Test
  void emptyTokenIsDropped() {
    Parsed p = QuerySyntax.parse("tag: mot");
    assertThat(p.tags()).isEmpty();
    assertThat(p.text()).isEqualTo("mot");
  }

  @Test
  void beforeAtThreeGranularities() {
    assertThat(QuerySyntax.parse("avant:2026").before())
        .isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(QuerySyntax.parse("avant:2026-03").before())
        .isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
    assertThat(QuerySyntax.parse("avant:2026-03-15").before())
        .isEqualTo(Instant.parse("2026-03-15T00:00:00Z"));
    assertThat(QuerySyntax.parse("before:2026-03").before())
        .isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
  }

  @Test
  void afterIsStrictlyAfterThePeriodAtThreeGranularities() {
    assertThat(QuerySyntax.parse("après:2026").after())
        .isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    assertThat(QuerySyntax.parse("après:2026-06").after())
        .isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
    assertThat(QuerySyntax.parse("après:2026-12").after())
        .isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    assertThat(QuerySyntax.parse("après:2026-06-15").after())
        .isEqualTo(Instant.parse("2026-06-16T00:00:00Z"));
    assertThat(QuerySyntax.parse("after:2026-06-30").after())
        .isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
  }

  @Test
  void invalidDateIsAnError() {
    Parsed p = QuerySyntax.parse("avant:2026-13 mot");
    assertThat(p.errors()).containsExactly("avant:2026-13");
    assertThat(p.before()).isNull();
    assertThat(p.text()).isEqualTo("mot");
    assertThat(QuerySyntax.parse("après:2026-02-30").errors()).hasSize(1);
    assertThat(QuerySyntax.parse("avant:hier").errors()).hasSize(1);
    assertThat(QuerySyntax.parse("après:26-06").errors()).hasSize(1);
  }

  @Test
  void severalBoundsKeepTheNarrowestWindow() {
    Parsed p = QuerySyntax.parse("avant:2026-09 avant:2026-06 après:2026-01 après:2026-03");
    assertThat(p.before()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
    assertThat(p.after()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
  }

  @Test
  void allTokensTogether() {
    Parsed p =
        QuerySyntax.parse("licenciement tag:travail émotion:anxiété avant:2026-09 après:2026-06");
    assertThat(p.text()).isEqualTo("licenciement");
    assertThat(p.tags()).containsExactly("travail");
    assertThat(p.emotions()).containsExactly("anxiété");
    assertThat(p.before()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    assertThat(p.after()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
    assertThat(p.errors()).isEmpty();
  }
}
