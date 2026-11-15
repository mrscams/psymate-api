package com.psymate.clinical.export.service;

import com.psymate.agenda.api.AppointmentExportView;
import com.psymate.clinical.export.model.ExportDocument.AppointmentBlock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Rendez-vous de l'export (G Q61, M11-11) : mise en forme d'un {@link AppointmentExportView} en
 * cellules prêtes à afficher, et tableau Markdown commun au fichier {@code rendez-vous.md} de
 * l'export du compte et à la section « Rendez-vous » d'un dossier. Dates et heures en UTC, comme le
 * reste de l'export ({@link ExportDates}, en-tête « Dates et heures en UTC »).
 */
final class AppointmentRows {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private AppointmentRows() {}

  /**
   * Cellules d'un rendez-vous ; {@code withPerson} garde la personne et le téléphone de l'invité
   * (export du compte), sinon ils sont nuls (dossier d'un patient).
   */
  static AppointmentBlock block(AppointmentExportView a, ExportTexts t, boolean withPerson) {
    return new AppointmentBlock(
        ExportDates.day(a.startsAt().atOffset(ZoneOffset.UTC).toLocalDate()),
        t.get("appointments.hours", time(a.startsAt()), time(a.endsAt())),
        t.get("appointments.duration", String.valueOf(a.durationMin())),
        t.get("session.modality." + a.modality()),
        t.get("appointments.status." + a.status()),
        withPerson ? a.subjectDisplayName() : null,
        withPerson ? blankToNull(a.guestPhone()) : null,
        t.get(a.seriesId() == null ? "appointments.series.no" : "appointments.series.yes"),
        blankToNull(a.planningNote()),
        blankToNull(a.cancelReason()),
        a.cancelledAt() == null ? null : ExportDates.dayTime(a.cancelledAt()));
  }

  static List<AppointmentBlock> blocks(
      List<AppointmentExportView> views, ExportTexts t, boolean withPerson) {
    return views.stream().map(v -> block(v, t, withPerson)).toList();
  }

  /** En-têtes des colonnes, dans l'ordre des cellules de {@link #cells}. */
  static List<String> headers(ExportTexts t, boolean withPerson) {
    return withPerson
        ? List.of(
            t.get("appointments.col.date"),
            t.get("appointments.col.hours"),
            t.get("appointments.col.duration"),
            t.get("appointments.col.modality"),
            t.get("appointments.col.status"),
            t.get("appointments.col.person"),
            t.get("appointments.col.phone"),
            t.get("appointments.col.series"),
            t.get("appointments.col.planningNote"),
            t.get("appointments.col.cancelReason"),
            t.get("appointments.col.cancelledAt"))
        : List.of(
            t.get("appointments.col.date"),
            t.get("appointments.col.hours"),
            t.get("appointments.col.duration"),
            t.get("appointments.col.modality"),
            t.get("appointments.col.status"),
            t.get("appointments.col.series"),
            t.get("appointments.col.planningNote"),
            t.get("appointments.col.cancelReason"),
            t.get("appointments.col.cancelledAt"));
  }

  /** Cellules d'un rendez-vous, chaîne vide pour une valeur absente. */
  static List<String> cells(AppointmentBlock b, boolean withPerson) {
    return withPerson
        ? List.of(
            b.date(),
            b.hours(),
            b.duration(),
            b.modality(),
            b.status(),
            orEmpty(b.person()),
            orEmpty(b.phone()),
            b.series(),
            orEmpty(b.planningNote()),
            orEmpty(b.cancelReason()),
            orEmpty(b.cancelledAt()))
        : List.of(
            b.date(),
            b.hours(),
            b.duration(),
            b.modality(),
            b.status(),
            b.series(),
            orEmpty(b.planningNote()),
            orEmpty(b.cancelReason()),
            orEmpty(b.cancelledAt()));
  }

  /**
   * Tableau Markdown : une ligne par rendez-vous. Le texte libre (note, motif, nom) est échappé et
   * mis sur une ligne ({@link MarkdownRenderer#escape}) : une barre verticale ou un saut de ligne
   * ne casse pas le tableau.
   */
  static void markdownTable(
      StringBuilder out, List<AppointmentBlock> blocks, ExportTexts t, boolean withPerson) {
    List<String> headers = headers(t, withPerson);
    row(out, headers);
    out.append('|');
    headers.forEach(h -> out.append(" --- |"));
    out.append('\n');
    for (AppointmentBlock b : blocks) {
      row(out, cells(b, withPerson));
    }
  }

  private static void row(StringBuilder out, List<String> cells) {
    out.append('|');
    for (String cell : cells) {
      out.append(' ').append(MarkdownRenderer.escape(cell)).append(" |");
    }
    out.append('\n');
  }

  private static String time(Instant instant) {
    return TIME.format(instant.atOffset(ZoneOffset.UTC));
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
