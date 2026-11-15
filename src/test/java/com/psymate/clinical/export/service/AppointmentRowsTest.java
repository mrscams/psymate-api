package com.psymate.clinical.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.agenda.api.AppointmentExportView;
import com.psymate.clinical.export.model.ExportDocument;
import com.psymate.clinical.export.model.ExportDocument.AppointmentBlock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** M11-11 (G Q61) : mise en forme des rendez-vous de l'export, sans base. */
class AppointmentRowsTest {

  private static final ExportTexts FR = ExportTexts.of(Locale.FRENCH);
  private static final ExportTexts EN = ExportTexts.of(Locale.ENGLISH);

  private static AppointmentExportView view(
      String status, String note, UUID seriesId, String phone, Instant cancelledAt) {
    return new AppointmentExportView(
        UUID.randomUUID(),
        null,
        "Sara Benali",
        phone,
        Instant.parse("2026-12-04T23:30:00Z"),
        Instant.parse("2026-12-05T00:20:00Z"),
        50,
        "phone",
        status,
        seriesId,
        note,
        cancelledAt == null ? null : "Absent",
        cancelledAt);
  }

  @Test
  void cellsAreFormattedInUtcWithLocalisedLabels() {
    AppointmentBlock b =
        AppointmentRows.block(
            view(
                "cancelled",
                "  note  ",
                UUID.randomUUID(),
                "+33 6 12 34 56 78",
                Instant.parse("2026-11-20T07:30:00Z")),
            FR,
            true);
    assertThat(b.date()).isEqualTo("04/12/2026");
    assertThat(b.hours()).isEqualTo("23:30–00:20");
    assertThat(b.duration()).isEqualTo("50 min");
    assertThat(b.modality()).isEqualTo("Par téléphone");
    assertThat(b.status()).isEqualTo("Annulé");
    assertThat(b.person()).isEqualTo("Sara Benali");
    assertThat(b.phone()).isEqualTo("+33 6 12 34 56 78");
    assertThat(b.series()).isEqualTo("oui");
    assertThat(b.planningNote()).isEqualTo("note");
    assertThat(b.cancelReason()).isEqualTo("Absent");
    assertThat(b.cancelledAt()).isEqualTo("20/11/2026 07:30");

    AppointmentBlock en =
        AppointmentRows.block(view("tentative", null, null, null, null), EN, true);
    assertThat(en.status()).isEqualTo("To be confirmed");
    assertThat(en.series()).isEqualTo("no");
    assertThat(en.planningNote()).isNull();
  }

  @Test
  void withoutPersonDropsNameAndPhone() {
    AppointmentBlock b =
        AppointmentRows.block(view("planned", null, null, "0612", null), FR, false);
    assertThat(b.person()).isNull();
    assertThat(b.phone()).isNull();
    assertThat(AppointmentRows.cells(b, false)).hasSize(AppointmentRows.headers(FR, false).size());
    AppointmentBlock withPerson =
        AppointmentRows.block(view("planned", null, null, "0612", null), FR, true);
    assertThat(AppointmentRows.cells(withPerson, true))
        .hasSize(AppointmentRows.headers(FR, true).size());
  }

  @Test
  void freeTextCannotBreakTheTable() {
    StringBuilder out = new StringBuilder();
    AppointmentRows.markdownTable(
        out,
        List.of(
            AppointmentRows.block(
                view("planned", "a | b\n## pas un titre", null, null, null), FR, false)),
        FR,
        false);
    List<String> lines = out.toString().lines().toList();
    assertThat(lines).hasSize(3);
    assertThat(lines.get(2)).contains("a \\| b \\#\\# pas un titre");
  }

  @Test
  void documentWithoutAppointmentsKeepsTheOldShape() {
    ExportDocument doc =
        new ExportDocument("A", null, null, LocalDate.of(2026, 11, 1), List.of(), List.of());
    assertThat(doc.appointments()).isEmpty();
    assertThat(new MarkdownRenderer().render(doc, FR)).doesNotContain("## Rendez-vous");
  }
}
