package com.psymate.clinical.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.clinical.export.ExportTestSupport;
import com.psymate.clinical.export.model.ExportDocument;
import com.psymate.identity.api.CurrentPractitioner;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * M11-11 (G Q61) : l'export d'un dossier patient reçoit une section « Rendez-vous » après les
 * séances, avec les rendez-vous de ce patient seulement (annulés compris), sans la personne ; omise
 * si le patient n'en a pas. Markdown et PDF par l'API, HTML par le rendu interne.
 */
class PatientExportAppointmentsIT extends ExportTestSupport {

  @Autowired private PatientExportService exports;
  @Autowired private HtmlRenderer html;

  private UUID doctor;
  private UUID alice;
  private UUID bob;

  @BeforeEach
  void setUp() throws Exception {
    doctor = account("Dr Agenda", "approved");
    alice = patient(doctor, "Alice Martin", LocalDate.of(1985, 6, 12));
    bob = patient(doctor, "Bob Durand", null);

    String s = startSession(doctor, alice, Instant.parse("2026-09-01T09:00:00Z"));
    createEntry(doctor, alice, s, "{\"type\":\"note\",\"title\":\"Note A\",\"body\":\"Texte A\"}");
    closeSession(doctor, s, "Compte rendu A");

    clock.set(Instant.parse("2026-11-15T08:00:00Z"));
    appointment(
        alice, Instant.parse("2026-12-03T09:00:00Z"), ",\"planningNote\":\"Bilan | suite\"");
    String cancelled = appointment(alice, Instant.parse("2026-12-10T08:00:00Z"), "");
    clock.set(Instant.parse("2026-11-20T07:30:00Z"));
    post("/api/appointments/" + cancelled + "/cancel", "{\"reason\":\"Empêchement\"}", as(doctor))
        .andExpect(status().isOk());
    // Rendez-vous d'un autre patient du même praticien : jamais dans le dossier d'Alice.
    appointment(bob, Instant.parse("2026-12-04T14:00:00Z"), ",\"planningNote\":\"Note de Bob\"");
    clock.set(Instant.now());
  }

  private String appointment(UUID patient, Instant startsAt, String extra) throws Exception {
    return json(
        post(
                "/api/appointments",
                "{\"patientId\":\""
                    + patient
                    + "\",\"startsAt\":\""
                    + startsAt
                    + "\",\"durationMin\":50,\"modality\":\"video\""
                    + extra
                    + "}",
                as(doctor))
            .andExpect(status().isCreated()),
        "$.appointments[0].id");
  }

  private String markdown(UUID patient) throws Exception {
    return get("/api/patients/" + patient + "/export?format=md", as(doctor))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  @Test
  void markdownHasAppointmentsSectionAfterSessionsWithOnlyThisPatient() throws Exception {
    String md = markdown(alice);

    assertThat(md).contains("\n## Rendez-vous\n");
    assertThat(md.indexOf("## Séance du 01/09/2026 09:00"))
        .isPositive()
        .isLessThan(md.indexOf("## Rendez-vous"));
    assertThat(md)
        .contains(
            "| Date | Heures | Durée | Modalité | Statut | Série | Note de planification |"
                + " Motif d'annulation | Annulé le |");
    String section = md.substring(md.indexOf("## Rendez-vous"));
    List<String> rows = section.lines().filter(l -> l.startsWith("|")).skip(2).toList();
    assertThat(rows)
        .containsExactly(
            "| 03/12/2026 | 09:00–09:50 | 50 min | En visio | Prévu | non | Bilan \\| suite |  |  |",
            "| 10/12/2026 | 08:00–08:50 | 50 min | En visio | Annulé | non |  | Empêchement |"
                + " 20/11/2026 07:30 |");
    // Ni la personne ni les rendez-vous de Bob.
    assertThat(section).doesNotContain("Personne").doesNotContain("Alice Martin");
    assertThat(md).doesNotContain("Note de Bob");
  }

  @Test
  void patientWithoutAppointmentsHasNoSection() throws Exception {
    UUID carol = patient(doctor, "Carol Sans", null);
    assertThat(markdown(carol)).doesNotContain("Rendez-vous");
  }

  @Test
  void bobSeesOnlyHisOwnAppointment() throws Exception {
    String md = markdown(bob);
    assertThat(md).contains("## Rendez-vous").contains("Note de Bob");
    assertThat(md).doesNotContain("Empêchement").doesNotContain("Bilan");
  }

  @Test
  void pdfAndHtmlRenderTheSection() throws Exception {
    byte[] pdf =
        get("/api/patients/" + alice + "/export?format=pdf", as(doctor))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

    CurrentPractitioner principal =
        new CurrentPractitioner(
            doctor, tenant, emailOf(doctor), "export", "psychologist", "approved");
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    ExportDocument doc = exports.build(alice, Locale.FRENCH);
    String page = html.render(doc, ExportTexts.of(Locale.FRENCH));
    assertThat(doc.appointments()).hasSize(2);
    assertThat(page).contains("<h2>Rendez-vous</h2>").contains("<table class=\"appointments\">");
    assertThat(page.indexOf("<h2>Séance du 01/09/2026 09:00</h2>"))
        .isPositive()
        .isLessThan(page.indexOf("<h2>Rendez-vous</h2>"));
    assertThat(page).contains("<td>Bilan | suite</td>").contains("<td>Empêchement</td>");
  }
}
