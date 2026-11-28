package com.psymate.billing.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.psymate.billing.model.BillingException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Moteur de rendu des factures, propre à la facturation (G Q93 ; {@code clinical.export} n'est ni
 * touché ni appelé) : la feuille ({@link InvoiceSheet}) devient un XHTML (mise en page de la
 * maquette {@code paper} du web, en clair, A4, en français, de gauche à droite), puis un PDF par
 * openhtmltopdf (déjà au {@code pom.xml}). Tout texte saisi (désignations, adresses, noms) est
 * échappé.
 *
 * <p>Polices : comme l'export du dossier, une police système (Segoe UI, DejaVu Sans) est
 * enregistrée quand elle existe sur la machine ; à défaut openhtmltopdf retombe sur les polices PDF
 * intégrées (Helvetica), qui ne couvrent que le latin : un caractère hors de ce jeu devient « ? »
 * au lieu de faire échouer le rendu. Aucune police n'est embarquée dans les ressources.
 *
 * <p>Un échec de la bibliothèque devient 500 PDF_UNAVAILABLE ; le journal ne porte que la classe de
 * l'erreur, jamais le contenu de la facture (G Q95).
 */
@Component
class InvoicePdfRenderer {

  private static final Logger LOG = LoggerFactory.getLogger(InvoicePdfRenderer.class);

  /** Police système candidate : famille CSS, graisse, chemins du fichier selon l'OS. */
  private record SystemFont(String family, int weight, List<String> paths) {}

  /** Police trouvée sur la machine. */
  private record SystemFontFile(String family, int weight, File file) {}

  private static final List<SystemFont> CANDIDATES =
      List.of(
          new SystemFont("Segoe UI", 400, List.of("C:/Windows/Fonts/segoeui.ttf")),
          new SystemFont("Segoe UI", 700, List.of("C:/Windows/Fonts/segoeuib.ttf")),
          new SystemFont(
              "DejaVu Sans",
              400,
              List.of(
                  "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                  "/usr/share/fonts/dejavu/DejaVuSans.ttf",
                  "/usr/share/fonts/TTF/DejaVuSans.ttf",
                  "/Library/Fonts/DejaVuSans.ttf")),
          new SystemFont(
              "DejaVu Sans",
              700,
              List.of(
                  "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
                  "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
                  "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
                  "/Library/Fonts/DejaVuSans-Bold.ttf")));

  /**
   * Feuille de style : thème clair fixe (palette des classes {@code .paper} de la maquette, sans
   * les couleurs de l'interface), A4, pied de page répété par une boîte de marge.
   */
  static final String CSS =
      """
      @page {
        size: A4;
        margin: 18mm 18mm 24mm 18mm;
        @bottom-center { content: element(footer); }
      }
      body { font-family: 'Segoe UI', 'DejaVu Sans', Helvetica, Arial, sans-serif; font-size: 9.5pt;
             color: #1E2633; background: #FFFFFF; line-height: 1.35; margin: 0; }
      #footer { position: running(footer); font-size: 7.5pt; color: #5A6679; text-align: center;
                border-top: 0.5pt solid #D6DCE6; padding-top: 4pt; }
      #watermark { position: fixed; top: 100mm; left: 0; width: 100%; text-align: center;
                   font-size: 84pt; font-weight: bold; color: #EBEEF4; z-index: -1;
                   transform: rotate(-30deg); }
      table.layout { width: 100%; border-collapse: collapse; }
      table.layout td { vertical-align: top; padding: 0; }
      .who-name { font-size: 13pt; font-weight: bold; }
      .muted { color: #5A6679; }
      td.doc-title { text-align: right; }
      .title { font-size: 16pt; font-weight: bold; letter-spacing: 0.6pt; text-transform: uppercase; }
      .number { white-space: nowrap; }
      table.meta { margin: 22pt 0 16pt 0; }
      .k { font-size: 7.5pt; font-weight: bold; letter-spacing: 0.5pt; text-transform: uppercase;
           color: #5A6679; margin-bottom: 3pt; }
      .recipient-name { font-size: 11pt; font-weight: bold; }
      table.dates { width: 100%; border-collapse: collapse; }
      table.dates td { padding: 1pt 0; }
      table.dates td.label { color: #5A6679; padding-right: 12pt; }
      table.dates td.value { text-align: right; }
      table.lines { width: 100%; border-collapse: collapse; }
      table.lines th { font-size: 7.5pt; font-weight: bold; letter-spacing: 0.5pt;
                       text-transform: uppercase; color: #5A6679; text-align: left;
                       padding: 4pt 6pt 4pt 0; border-bottom: 0.75pt solid #1E2633; }
      table.lines td { padding: 6pt 6pt 6pt 0; border-bottom: 0.5pt solid #D6DCE6;
                       vertical-align: top; }
      table.lines th.num, table.lines td.num { text-align: right; }
      table.lines th.last, table.lines td.last { padding-right: 0; }
      table.lines tr { page-break-inside: avoid; }
      table.totals { width: 100%; border-collapse: collapse; margin-top: 10pt; }
      table.totals td { padding: 2pt 0; }
      table.totals td.amount { text-align: right; }
      table.totals tr.big td { border-top: 0.75pt solid #1E2633; padding-top: 6pt; font-size: 11pt;
                               font-weight: bold; }
      .legal { margin-top: 16pt; color: #5A6679; page-break-inside: avoid; }
      .legal p { margin: 0 0 3pt 0; }
      .stamp { margin-top: 18pt; padding: 3pt 10pt; border: 1.5pt solid #1E6B45; color: #1E6B45;
               font-size: 11pt; font-weight: bold; letter-spacing: 1pt; text-transform: uppercase;
               width: 40mm; text-align: center; transform: rotate(-4deg); }
      """;

  private final List<SystemFontFile> fonts;
  private final boolean unicodeFonts;

  InvoicePdfRenderer() {
    this.fonts =
        CANDIDATES.stream()
            .map(
                f ->
                    f.paths().stream()
                        .map(File::new)
                        .filter(File::isFile)
                        .findFirst()
                        .map(file -> new SystemFontFile(f.family(), f.weight(), file))
                        .orElse(null))
            .filter(Objects::nonNull)
            .toList();
    this.unicodeFonts = !fonts.isEmpty();
  }

  /** PDF de la feuille ; 500 PDF_UNAVAILABLE si la bibliothèque échoue. */
  byte[] render(InvoiceSheet sheet) {
    String html = html(sheet);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      for (SystemFontFile f : fonts) {
        builder.useFont(f.file(), f.family(), f.weight(), FontStyle.NORMAL, true);
      }
      builder.withHtmlContent(unicodeFonts ? html : latinOnly(html), null);
      builder.toStream(out);
      builder.run();
      return out.toByteArray();
    } catch (Exception e) {
      // Ni contenu ni message : le message d'une erreur de rendu peut citer le document.
      LOG.error("Invoice PDF rendering failed: {}", e.getClass().getName());
      throw BillingException.pdfUnavailable();
    }
  }

  /** XHTML bien formé de la feuille, chaque texte échappé. */
  static String html(InvoiceSheet s) {
    StringBuilder out = new StringBuilder(8192);
    out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n");
    out.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"fr\" dir=\"ltr\">\n<head>\n");
    out.append("<meta charset=\"UTF-8\"/>\n<title>")
        .append(esc(s.title() + " " + s.number()))
        .append("</title>\n<style>\n")
        .append(CSS)
        .append("</style>\n</head>\n<body>\n");
    out.append("<div id=\"footer\">").append(esc(s.footer())).append("</div>\n");
    if (s.watermark() != null) {
      out.append("<div id=\"watermark\">").append(esc(s.watermark())).append("</div>\n");
    }

    // Émetteur à gauche, titre et numéro à droite.
    out.append("<table class=\"layout head\"><tr><td class=\"who\">");
    out.append("<div class=\"who-name\">").append(esc(s.issuerName())).append("</div>");
    s.issuerLines()
        .forEach(l -> out.append("<div class=\"muted\">").append(esc(l)).append("</div>"));
    out.append("</td><td class=\"doc-title\">");
    out.append("<div class=\"title\">").append(esc(s.title())).append("</div>");
    out.append("<div class=\"number\">").append(esc(s.number())).append("</div>");
    out.append("</td></tr></table>\n");

    // Destinataire à gauche, dates à droite.
    out.append("<table class=\"layout meta\"><tr><td style=\"width: 55%\">");
    out.append("<div class=\"k\">").append(esc(s.recipientHeading())).append("</div>");
    out.append("<div class=\"recipient-name\">").append(esc(s.recipientName())).append("</div>");
    s.recipientLines()
        .forEach(l -> out.append("<div class=\"muted\">").append(esc(l)).append("</div>"));
    out.append("</td><td style=\"width: 45%\"><table class=\"dates\">");
    for (InvoiceSheet.Meta m : s.meta()) {
      out.append("<tr><td class=\"label\">")
          .append(esc(m.label()))
          .append("</td><td class=\"value\">")
          .append(esc(m.value()))
          .append("</td></tr>");
    }
    out.append("</table></td></tr></table>\n");

    // Lignes.
    out.append("<table class=\"lines\"><thead><tr>");
    List<String> columns = s.columns();
    for (int i = 0; i < columns.size(); i++) {
      out.append("<th class=\"").append(cellClass(i, columns.size())).append("\">");
      out.append(esc(columns.get(i))).append("</th>");
    }
    out.append("</tr></thead><tbody>\n");
    for (InvoiceSheet.Row r : s.rows()) {
      List<String> cells = List.of(r.date(), r.label(), r.quantity(), r.unitPrice(), r.amount());
      out.append("<tr>");
      for (int i = 0; i < cells.size(); i++) {
        out.append("<td class=\"").append(cellClass(i, cells.size())).append("\">");
        out.append(esc(cells.get(i))).append("</td>");
      }
      out.append("</tr>\n");
    }
    out.append("</tbody></table>\n");

    // Totaux à droite.
    out.append(
        "<table class=\"layout\"><tr><td style=\"width: 50%\"></td><td style=\"width: 50%\">");
    out.append("<table class=\"totals\">");
    for (InvoiceSheet.Total t : s.totals()) {
      out.append(t.big() ? "<tr class=\"big\">" : "<tr>")
          .append("<td>")
          .append(esc(t.label()))
          .append("</td><td class=\"amount\">")
          .append(esc(t.amount()))
          .append("</td></tr>");
    }
    out.append("</table></td></tr></table>\n");

    // Mentions, tampon.
    if (!s.legal().isEmpty()) {
      out.append("<div class=\"legal\">");
      s.legal().forEach(l -> out.append("<p>").append(esc(l)).append("</p>"));
      out.append("</div>\n");
    }
    if (s.stamp() != null) {
      out.append("<div class=\"stamp\">").append(esc(s.stamp())).append("</div>\n");
    }
    out.append("</body>\n</html>\n");
    return out.toString();
  }

  /** Date, désignation à gauche ; quantité, prix et montant à droite ; dernière sans marge. */
  private static String cellClass(int index, int count) {
    String align = index >= 2 ? "num" : "text";
    return index == count - 1 ? align + " last" : align;
  }

  /**
   * Échappement XML d'un texte (désignation, adresse, nom). Les caractères de contrôle que XML 1.0
   * interdit sont retirés : le document doit rester bien formé.
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
        || (cp >= 0xE000 && cp <= 0xFFFD)
        || (cp >= 0x10000 && cp <= 0x10FFFF);
  }

  /**
   * Remplace par « ? » ce que les polices PDF intégrées (WinAnsi, windows-1252) ne savent pas
   * dessiner, quand aucune police système n'a été trouvée.
   */
  static String latinOnly(String html) {
    CharsetEncoder encoder = Charset.forName("windows-1252").newEncoder();
    StringBuilder sb = new StringBuilder(html.length());
    html.codePoints()
        .forEach(
            cp -> {
              if (cp < 0x80 || (cp <= 0xFFFF && encoder.canEncode((char) cp))) {
                sb.appendCodePoint(cp);
              } else {
                sb.append('?');
              }
            });
    return sb.toString();
  }
}
