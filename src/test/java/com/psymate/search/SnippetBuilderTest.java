package com.psymate.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SnippetBuilderTest {

  @Test
  void escapesHtmlThenTurnsDelimitersIntoMarks() {
    String marked = "a <script> " + SnippetBuilder.OPEN + "mot" + SnippetBuilder.CLOSE + " & b";
    assertThat(SnippetBuilder.fromMarked(marked))
        .isEqualTo("a &lt;script&gt; <mark>mot</mark> &amp; b");
    assertThat(SnippetBuilder.fromMarked(null)).isNull();
  }

  @Test
  void plainTruncatesLongTextOnAWordBoundary() {
    String text = "mot ".repeat(100);
    String out = SnippetBuilder.plain(text);
    assertThat(out).endsWith("…");
    assertThat(out.length()).isLessThanOrEqualTo(201);
    assertThat(SnippetBuilder.plain("court <b>")).isEqualTo("court &lt;b&gt;");
    assertThat(SnippetBuilder.plain("  ")).isNull();
  }

  @Test
  void aroundMarksTheClosestWord() {
    String body = "Aujourd'hui nous avons parlé du licenciement et de ses conséquences.";
    String out = SnippetBuilder.around(body, "licencimen");
    assertThat(out).contains("<mark>licenciement</mark>");
  }

  @Test
  void aroundFindsAccentedWordsWithTheirAccents() {
    String out = SnippetBuilder.around("Une grande anxiété le soir", "anxiete");
    assertThat(out).contains("<mark>anxiété</mark>");
  }

  @Test
  void aroundFallsBackToTheStartWhenNothingIsClose() {
    assertThat(SnippetBuilder.around("rien de commun ici", "zzzzzz"))
        .isEqualTo("rien de commun ici");
  }

  @Test
  void highlightKeepsTheAccentedTextWhenTheRawHeadlineHasMarks() {
    String raw =
        "Une grande " + SnippetBuilder.OPEN + "anxiété" + SnippetBuilder.CLOSE + " le soir";
    assertThat(SnippetBuilder.highlight("Une grande anxiété le soir", raw, "autre"))
        .isEqualTo("Une grande <mark>anxiété</mark> le soir");
  }

  @Test
  void highlightRemapsTheMarksOfTheUnaccentedHeadlineOntoTheOriginalText() {
    String original = "Une journée très éprouvante, puis un déménagement chaotique";
    String folded =
        "Une "
            + SnippetBuilder.OPEN
            + "journee"
            + SnippetBuilder.CLOSE
            + " tres eprouvante ... un "
            + SnippetBuilder.OPEN
            + "demenagement"
            + SnippetBuilder.CLOSE
            + " chaotique";
    assertThat(SnippetBuilder.highlight(original, "Une journée très éprouvante", folded))
        .isEqualTo(
            "Une <mark>journée</mark> très éprouvante ... un <mark>déménagement</mark> chaotique");
  }

  @Test
  void highlightFallsBackToTheRawHeadlineWhenTheTextCannotBeAligned() {
    String folded = SnippetBuilder.OPEN + "coeur" + SnippetBuilder.CLOSE + " gros";
    assertThat(SnippetBuilder.highlight("Un cœur très gros", "Un cœur très gros", folded))
        .isEqualTo("Un cœur très gros");
  }
}
