package com.psymate.clinical.export;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.psymate.shared.problem.ProblemException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Génère le PDF depuis le HTML du {@link HtmlRenderer} (openhtmltopdf + PDFBox, licence LGPL 2.1+).
 * La police système (Segoe UI, DejaVu Sans) est enregistrée quand elle existe sur la machine ; à
 * défaut openhtmltopdf retombe sur les polices PDF intégrées (Helvetica), qui ne couvrent que le
 * latin : les caractères hors de ce jeu sont alors remplacés par « ? » plutôt que de faire échouer
 * l'export. Toute erreur de génération devient un 500 EXPORT_FAILED au détail générique.
 */
@Component
class PdfRenderer {

  private static final Logger LOG = LoggerFactory.getLogger(PdfRenderer.class);

  /** Police système candidate : famille CSS, graisse, chemins du fichier selon l'OS. */
  private record SystemFont(String family, int weight, List<String> paths) {}

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

  private final List<SystemFontFile> fonts;
  private final boolean unicodeFonts;

  /** Police trouvée sur la machine. */
  private record SystemFontFile(String family, int weight, File file) {}

  PdfRenderer() {
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
            .filter(java.util.Objects::nonNull)
            .toList();
    this.unicodeFonts = !fonts.isEmpty();
  }

  byte[] render(String html) {
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
      LOG.error("PDF generation failed", e);
      throw new ProblemException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "EXPORT_FAILED",
          "Export impossible",
          "La génération du PDF a échoué. Réessayez, ou exportez en Markdown.");
    }
  }

  /**
   * Remplace par « ? » ce que les polices PDF intégrées (WinAnsi, windows-1252) ne savent pas
   * dessiner.
   */
  static String latinOnly(String html) {
    java.nio.charset.CharsetEncoder encoder =
        java.nio.charset.Charset.forName("windows-1252").newEncoder();
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
