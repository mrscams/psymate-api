package com.psymate.clinical.export.service;

import com.psymate.clinical.export.model.ExportDocument;
import com.psymate.clinical.export.model.ExportDocument.EntryBlock;
import com.psymate.clinical.export.model.ExportDocument.SessionBlock;
import org.springframework.stereotype.Component;

/**
 * Rendu Markdown du modèle d'export. Le texte libre (titres, corps) est neutralisé : les caractères
 * de mise en forme sont échappés dans les titres et le corps est placé dans une citation, pour
 * qu'une note ne puisse pas casser la structure du document.
 */
@Component
class MarkdownRenderer {

  String render(ExportDocument doc, ExportTexts t) {
    StringBuilder out = new StringBuilder();
    out.append("# ").append(t.get("title", escape(doc.patientName()))).append("\n\n");
    if (doc.birthDate() != null) {
      out.append("- ")
          .append(
              t.get("patient.birth", ExportDates.day(doc.birthDate()), String.valueOf(doc.age())))
          .append('\n');
    } else {
      out.append("- ").append(t.get("patient.birth.unknown")).append('\n');
    }
    out.append("- ").append(t.get("exportedOn", ExportDates.day(doc.exportedOn()))).append('\n');
    out.append("- ")
        .append(
            t.get("counts", String.valueOf(doc.sessionCount()), String.valueOf(doc.entryCount())))
        .append('\n');
    out.append("- ").append(t.get("timezone")).append('\n');
    out.append("- ").append(t.get("scopeNote")).append("\n\n");
    out.append("_")
        .append(t.get("header", t.appName(), ExportDates.day(doc.exportedOn())))
        .append("_\n");

    if (doc.sessions().isEmpty() && doc.unassigned().isEmpty()) {
      out.append('\n').append(t.get("empty")).append('\n');
    }
    for (SessionBlock s : doc.sessions()) {
      out.append("\n## ").append(escape(s.heading())).append("\n\n");
      out.append('_').append(escape(s.details())).append("_\n");
      if (s.title() != null) {
        out.append("\n**").append(escape(s.title())).append("**\n");
      }
      quote(out, s.body());
      for (EntryBlock e : s.entries()) {
        entry(out, e, t);
      }
    }
    if (!doc.unassigned().isEmpty()) {
      out.append("\n## ").append(t.get("noSession")).append('\n');
      for (EntryBlock e : doc.unassigned()) {
        entry(out, e, t);
      }
    }
    return out.toString();
  }

  private void entry(StringBuilder out, EntryBlock e, ExportTexts t) {
    out.append("\n### ")
        .append(escape(e.title() != null ? e.title() : e.typeLabel()))
        .append("\n\n");
    out.append('_').append(escape(e.typeLabel())).append(" · ").append(escape(e.dateLabel()));
    if (e.draft()) {
      out.append(" · ").append(t.get("draft"));
    }
    out.append("_\n");
    quote(out, e.body());
    if (e.feeling() != null || e.medication() != null) {
      out.append('\n');
    }
    if (e.feeling() != null) {
      out.append("- ").append(escape(e.feeling())).append('\n');
    }
    if (e.medication() != null) {
      out.append("- ").append(t.get("entry.medication", escape(e.medication()))).append('\n');
    }
    if (!e.tags().isEmpty()) {
      out.append("\n- ")
          .append(t.get("entry.tags", escape(String.join(", ", e.tags()))))
          .append('\n');
    }
    if (!e.links().isEmpty()) {
      out.append("\n- ").append(t.get("entry.links")).append('\n');
      for (String link : e.links()) {
        out.append("  - ").append(escape(link)).append('\n');
      }
    }
  }

  /** Corps en citation : chaque ligne préfixée, rien du texte libre ne devient structure. */
  private static void quote(StringBuilder out, String body) {
    if (body == null) {
      return;
    }
    out.append('\n');
    for (String line : body.strip().split("\\R", -1)) {
      out.append("> ").append(line.stripTrailing()).append('\n');
    }
  }

  /** Échappe les caractères de mise en forme Markdown d'un texte sur une ligne. */
  static String escape(String text) {
    if (text == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(text.length() + 8);
    for (char c : text.replaceAll("\\s+", " ").toCharArray()) {
      if ("\\`*_[]<>#|".indexOf(c) >= 0) {
        sb.append('\\');
      }
      sb.append(c);
    }
    return sb.toString();
  }
}
