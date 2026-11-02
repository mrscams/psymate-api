package com.psymate.clinical.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.clinical.export.model.ExportDocument;
import com.psymate.clinical.export.model.ExportDocument.EntryBlock;
import com.psymate.clinical.export.model.ExportDocument.SessionBlock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Mise en page Markdown et HTML du modèle d'export, sans base ni contexte Spring. */
class MarkdownRendererTest {

  private static final ExportTexts FR = ExportTexts.of(Locale.FRENCH);

  private final MarkdownRenderer markdown = new MarkdownRenderer();
  private final HtmlRenderer html = new HtmlRenderer();

  private static EntryBlock entry(String title, String body, List<String> links) {
    return new EntryBlock(
        "note", "Note", "01/09/2026 09:10", title, body, false, null, null, List.of(), links);
  }

  private static ExportDocument document(EntryBlock... entries) {
    SessionBlock session =
        new SessionBlock(
            "session",
            "Séance du 01/09/2026 09:00",
            "Séance · En cabinet · 50 min",
            null,
            "Compte rendu",
            List.of(entries));
    return new ExportDocument(
        "Alice Martin",
        LocalDate.of(1985, 6, 12),
        41,
        LocalDate.of(2026, 11, 1),
        List.of(session),
        List.of());
  }

  @Test
  void headerSessionAndEntriesFollowTheModelOrder() {
    String md =
        markdown.render(
            document(
                entry("Premier", "texte 1", List.of("déclenche : Second")),
                entry("Second", "texte 2", List.of())),
            FR);
    assertThat(md).startsWith("# Dossier de Alice Martin");
    assertThat(md).contains("Né(e) le 12/06/1985 (41 ans)");
    assertThat(md).contains("1 séance(s) et 2 entrée(s) exportée(s)");
    assertThat(md).contains("Export PsyMate du 01/11/2026 · usage professionnel");
    assertThat(md.indexOf("## Séance du 01/09/2026 09:00"))
        .isLessThan(md.indexOf("### Premier"))
        .isPositive();
    assertThat(md.indexOf("### Premier")).isLessThan(md.indexOf("### Second"));
    assertThat(md).contains("  - déclenche : Second");
  }

  @Test
  void freeTextCannotBreakTheMarkdownStructure() {
    String md =
        markdown.render(
            document(entry("# Faux *titre*", "# pas un titre\n---\n## ni ça", List.of())), FR);
    assertThat(md).contains("### \\# Faux \\*titre\\*");
    // Le corps reste dans une citation : aucune ligne du texte libre ne devient un titre.
    assertThat(md).contains("> # pas un titre").contains("> ## ni ça");
    assertThat(md.lines().filter(l -> l.startsWith("## ")).toList())
        .containsExactly("## Séance du 01/09/2026 09:00");
  }

  @Test
  void noSessionSectionIsRenderedLast() {
    ExportDocument doc =
        new ExportDocument(
            "Alice Martin",
            null,
            null,
            LocalDate.of(2026, 11, 1),
            List.of(),
            List.of(entry("Orpheline", "x", List.of())));
    String md = markdown.render(doc, FR);
    assertThat(md).contains("Date de naissance non renseignée").contains("## Sans séance");
    assertThat(md.indexOf("## Sans séance")).isLessThan(md.indexOf("### Orpheline"));
  }

  @Test
  void htmlIsWellFormedXmlWithLightThemeAndRunningHeader() throws Exception {
    ExportDocument doc =
        document(entry("Titre <b>& \"guillemets\"", "ligne 1\nligne 2 \u0001 fin", List.of()));
    String page = html.render(doc, FR);

    assertThat(page).contains("&lt;b&gt;&amp; &quot;guillemets&quot;");
    assertThat(page).doesNotContain("<b>");
    assertThat(page).contains("ligne 1<br/>ligne 2  fin");
    assertThat(page).contains("background: #FFFFFF").contains("position: running(pageHeader)");
    assertThat(page).contains("counter(page)").contains("counter(pages)");
    assertThat(page).contains("Export PsyMate du 01/11/2026 · usage professionnel");
    assertThat(page).contains("'Segoe UI', 'DejaVu Sans', Helvetica, Arial, sans-serif");

    // Bien formé : openhtmltopdf lit du XML.
    javax.xml.parsers.DocumentBuilderFactory factory =
        javax.xml.parsers.DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
    factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(page.getBytes("UTF-8")));
  }

  @Test
  void textsFallBackToFrenchAndExposeTheProductName() {
    assertThat(FR.appName()).isEqualTo("PsyMate");
    assertThat(ExportTexts.of(Locale.GERMAN).locale()).isEqualTo(Locale.FRENCH);
    assertThat(ExportTexts.of(Locale.ENGLISH).get("noSession")).isEqualTo("No session");
    assertThat(FR.get("cle.inconnue")).isEqualTo("clinical.export.cle.inconnue");
  }

  @Test
  void nonLatinCharactersDegradeToQuestionMarksWithoutSystemFonts() {
    assertThat(PdfRenderer.latinOnly("Élodie – œuvre Мария ’")).isEqualTo("Élodie – œuvre ????? ’");
  }
}
