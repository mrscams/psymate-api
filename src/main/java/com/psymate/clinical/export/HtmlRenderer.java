package com.psymate.clinical.export;

import com.psymate.clinical.export.ExportDocument.EntryBlock;
import com.psymate.clinical.export.ExportDocument.SessionBlock;
import org.springframework.stereotype.Component;

/**
 * Rendu HTML (XHTML bien formé) du modèle d'export, destiné à openhtmltopdf. Thème clair fixe, quel
 * que soit le réglage de l'écran (Étude couleurs : les exports PDF utilisent le thème clair). Le
 * HTML est produit directement depuis le modèle, sans passer par une bibliothèque Markdown.
 */
@Component
class HtmlRenderer {

  /**
   * Feuille de style de l'export, thème clair, Étude couleurs §8 : fond blanc, texte sombre,
   * palette sobre ; aucune couleur de marque en dur ailleurs. Police système avec repli sur les
   * polices PDF intégrées. L'en-tête de page courant et le pied « page X / Y » sont posés par les
   * boîtes de marge de {@code @page}.
   */
  static final String CSS =
      """
      @page {
        size: A4;
        margin: 24mm 18mm 20mm 18mm;
        @top-center { content: element(pageHeader); }
        @bottom-center { content: "%PAGE% " counter(page) " / " counter(pages); \
      font-family: 'Segoe UI', 'DejaVu Sans', Helvetica, Arial, sans-serif; font-size: 9pt; \
      color: #5B6472; }
      }
      body { font-family: 'Segoe UI', 'DejaVu Sans', Helvetica, Arial, sans-serif; font-size: 10.5pt;
             color: #1F2933; background: #FFFFFF; line-height: 1.4; }
      #pageHeader { position: running(pageHeader); font-size: 9pt; color: #5B6472;
                    border-bottom: 0.5pt solid #C9D1DA; padding-bottom: 3pt; text-align: center; }
      h1 { font-size: 20pt; margin: 0 0 8pt 0; color: #111827; }
      h2 { font-size: 14pt; margin: 18pt 0 4pt 0; color: #111827; border-bottom: 0.5pt solid #C9D1DA;
           padding-bottom: 2pt; page-break-after: avoid; }
      h3 { font-size: 11.5pt; margin: 10pt 0 2pt 0; color: #1F2933; page-break-after: avoid; }
      ul { margin: 2pt 0 2pt 0; padding-left: 16pt; }
      p { margin: 2pt 0; }
      .facts { margin: 0 0 6pt 0; }
      .meta { font-size: 9.5pt; color: #5B6472; margin: 0 0 3pt 0; }
      .body { margin: 3pt 0 3pt 0; padding-left: 8pt; border-left: 2pt solid #C9D1DA; }
      .entry { page-break-inside: avoid; }
      """;

  String render(ExportDocument doc, ExportTexts t) {
    StringBuilder out = new StringBuilder(4096);
    out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    out.append("<!DOCTYPE html>\n");
    out.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"")
        .append(t.locale().getLanguage())
        .append("\">\n<head>\n<meta charset=\"UTF-8\"/>\n<title>")
        .append(esc(t.get("title", doc.patientName())))
        .append("</title>\n<style>\n")
        .append(CSS.replace("%PAGE%", cssString(t.get("page"))))
        .append("</style>\n</head>\n<body>\n");
    out.append("<div id=\"pageHeader\">")
        .append(esc(t.get("header", t.appName(), ExportDates.day(doc.exportedOn()))))
        .append("</div>\n");
    out.append("<h1>").append(esc(t.get("title", doc.patientName()))).append("</h1>\n");
    out.append("<ul class=\"facts\">\n");
    if (doc.birthDate() != null) {
      li(out, t.get("patient.birth", ExportDates.day(doc.birthDate()), String.valueOf(doc.age())));
    } else {
      li(out, t.get("patient.birth.unknown"));
    }
    li(out, t.get("exportedOn", ExportDates.day(doc.exportedOn())));
    li(out, t.get("counts", String.valueOf(doc.sessionCount()), String.valueOf(doc.entryCount())));
    li(out, t.get("timezone"));
    li(out, t.get("scopeNote"));
    out.append("</ul>\n");
    if (doc.sessions().isEmpty() && doc.unassigned().isEmpty()) {
      out.append("<p>").append(esc(t.get("empty"))).append("</p>\n");
    }
    for (SessionBlock s : doc.sessions()) {
      out.append("<h2>").append(esc(s.heading())).append("</h2>\n");
      out.append("<p class=\"meta\">").append(esc(s.details())).append("</p>\n");
      if (s.title() != null) {
        out.append("<p><strong>").append(esc(s.title())).append("</strong></p>\n");
      }
      body(out, s.body());
      for (EntryBlock e : s.entries()) {
        entry(out, e, t);
      }
    }
    if (!doc.unassigned().isEmpty()) {
      out.append("<h2>").append(esc(t.get("noSession"))).append("</h2>\n");
      for (EntryBlock e : doc.unassigned()) {
        entry(out, e, t);
      }
    }
    out.append("</body>\n</html>\n");
    return out.toString();
  }

  private void entry(StringBuilder out, EntryBlock e, ExportTexts t) {
    out.append("<div class=\"entry\">\n");
    out.append("<h3>").append(esc(e.title() != null ? e.title() : e.typeLabel())).append("</h3>\n");
    out.append("<p class=\"meta\">")
        .append(esc(e.typeLabel()))
        .append(" · ")
        .append(esc(e.dateLabel()));
    if (e.draft()) {
      out.append(" · ").append(esc(t.get("draft")));
    }
    out.append("</p>\n");
    body(out, e.body());
    if (e.feeling() != null || e.medication() != null || !e.tags().isEmpty()) {
      out.append("<ul>\n");
      if (e.feeling() != null) {
        li(out, e.feeling());
      }
      if (e.medication() != null) {
        li(out, t.get("entry.medication", e.medication()));
      }
      if (!e.tags().isEmpty()) {
        li(out, t.get("entry.tags", String.join(", ", e.tags())));
      }
      out.append("</ul>\n");
    }
    if (!e.links().isEmpty()) {
      out.append("<p class=\"meta\">").append(esc(t.get("entry.links"))).append("</p>\n<ul>\n");
      e.links().forEach(l -> li(out, l));
      out.append("</ul>\n");
    }
    out.append("</div>\n");
  }

  private static void li(StringBuilder out, String text) {
    out.append("<li>").append(esc(text)).append("</li>\n");
  }

  private static void body(StringBuilder out, String body) {
    if (body == null) {
      return;
    }
    out.append("<p class=\"body\">");
    String[] lines = body.strip().split("\\R", -1);
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        out.append("<br/>");
      }
      out.append(esc(lines[i].stripTrailing()));
    }
    out.append("</p>\n");
  }

  /**
   * Échappement XML d'un texte libre. Les caractères de contrôle que XML 1.0 interdit sont retirés
   * (le document doit rester bien formé, sinon openhtmltopdf refuse de le lire).
   */
  static String esc(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(text.length() + 16);
    text.codePoints()
        .forEach(
            cp -> {
              switch (cp) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> {
                  if (isXmlChar(cp)) {
                    sb.appendCodePoint(cp);
                  }
                }
              }
            });
    return sb.toString();
  }

  private static boolean isXmlChar(int cp) {
    return cp == 0x9
        || cp == 0xA
        || cp == 0xD
        || (cp >= 0x20 && cp <= 0xD7FF)
        || (cp >= 0xE000 && cp <= 0xFFFD && cp != 0xFFFE && cp != 0xFFFF)
        || (cp >= 0x10000 && cp <= 0x10FFFF);
  }

  /** Texte court placé dans une chaîne CSS : guillemets et barres obliques neutralisés. */
  private static String cssString(String text) {
    return text.replace("\\", "").replace("\"", "").replace("\n", " ");
  }
}
