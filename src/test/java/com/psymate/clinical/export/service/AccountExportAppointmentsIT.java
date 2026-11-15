package com.psymate.clinical.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.clinical.export.ExportTestSupport;
import com.psymate.identity.api.CurrentPractitioner;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * M11-11 (G Q61) : l'export du compte contient {@code rendez-vous.md}, tous les rendez-vous du
 * praticien (patients et invités, purgés et annulés compris), jamais ceux d'un autre praticien. Les
 * rendez-vous sont créés par l'API de l'agenda ; le zip est celui que construit le travail d'export
 * ({@link ExportJobRunner#buildZip}), appelé directement pour ne dépendre d'aucune attente.
 */
class AccountExportAppointmentsIT extends ExportTestSupport {

  private static final String SARA =
      "{\"lastName\":\"Benali\",\"firstName\":\"Sara\",\"phone\":\"+33 6 12 34 56 78\"}";

  @Autowired private ExportJobRunner runner;

  private UUID doctor;
  private UUID alice;

  @BeforeEach
  void setUp() {
    doctor = account("Dr Agenda", "approved");
    alice = patient(doctor, "Alice Martin", LocalDate.of(1985, 6, 12));
  }

  // ---- outils

  private String appointment(UUID owner, Instant startsAt, String subject, String extra)
      throws Exception {
    return json(
        post(
                "/api/appointments",
                "{\"startsAt\":\""
                    + startsAt
                    + "\",\"durationMin\":50,\"modality\":\"in_person\","
                    + subject
                    + extra
                    + "}",
                as(owner))
            .andExpect(status().isCreated()),
        "$.appointments[0].id");
  }

  private String patientAppointment(UUID owner, UUID patient, Instant startsAt, String extra)
      throws Exception {
    return appointment(owner, startsAt, "\"patientId\":\"" + patient + "\"", extra);
  }

  private String guestAppointment(UUID owner, String guest, Instant startsAt) throws Exception {
    return appointment(owner, startsAt, "\"guest\":" + guest, "");
  }

  private String rendezVous(UUID practitioner) throws IOException {
    CurrentPractitioner principal =
        new CurrentPractitioner(
            practitioner, tenant, emailOf(practitioner), "export", "psychologist", "approved");
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    Map<String, byte[]> files = new LinkedHashMap<>();
    try (ZipInputStream in =
        new ZipInputStream(
            new ByteArrayInputStream(runner.buildZip(practitioner)), StandardCharsets.UTF_8)) {
      for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
        files.put(e.getName(), in.readAllBytes());
      }
    } finally {
      SecurityContextHolder.clearContext();
    }
    assertThat(files.keySet()).contains("patients.md", "rendez-vous.md");
    return new String(files.get("rendez-vous.md"), StandardCharsets.UTF_8);
  }

  private static List<String> tableRows(String md) {
    // Lignes du tableau, sans l'en-tête ni le séparateur.
    List<String> table = md.lines().filter(l -> l.startsWith("|")).toList();
    return table.size() < 2 ? List.of() : table.subList(2, table.size());
  }

  // ---- critères

  @Test
  void accountZipListsEveryAppointmentOfThePractitionerAndNoOther() throws Exception {
    clock.set(Instant.parse("2026-11-15T08:00:00Z"));
    patientAppointment(
        doctor,
        alice,
        Instant.parse("2026-12-03T09:00:00Z"),
        ",\"planningNote\":\"Apporter le bilan\"");
    guestAppointment(doctor, SARA, Instant.parse("2026-12-04T14:00:00Z"));
    String purged =
        guestAppointment(
            doctor,
            "{\"lastName\":\"Durand\",\"firstName\":\"Paul\",\"phone\":\"01 02 03 04 05\"}",
            Instant.parse("2026-03-02T10:00:00Z"));
    // Purge de l'invité (G Q60) posée en SQL sur ce seul rendez-vous, comme la ferait le job.
    jdbc.update(
        "update appointment set guest_first_name = null, guest_last_name = null,"
            + " guest_phone = null, guest_purged_at = now() where id = ?",
        UUID.fromString(purged));
    String cancelled = patientAppointment(doctor, alice, Instant.parse("2026-12-10T08:00:00Z"), "");
    clock.set(Instant.parse("2026-11-20T07:30:00Z"));
    post(
            "/api/appointments/" + cancelled + "/cancel",
            "{\"reason\":\"Empêchement du patient\"}",
            as(doctor))
        .andExpect(status().isOk());

    // Un second praticien, avec ses propres patient et invité : absent de l'export.
    UUID other = account("Dr Autre", "approved");
    UUID karim = patient(other, "Karim Autre", null);
    patientAppointment(
        other, karim, Instant.parse("2026-12-05T09:00:00Z"), ",\"planningNote\":\"Secret\"");
    guestAppointment(
        other, "{\"lastName\":\"Inconnu\",\"phone\":\"+33 7 00 00 00 00\"}", Instant.now());

    String md = rendezVous(doctor);

    assertThat(md).startsWith("# Vos rendez-vous");
    assertThat(md).contains("Dates et heures en UTC.");
    assertThat(md)
        .contains(
            "| Date | Heures | Durée | Modalité | Statut | Personne | Téléphone | Série |"
                + " Note de planification | Motif d'annulation | Annulé le |");
    List<String> rows = tableRows(md);
    assertThat(rows).hasSize(4);
    // Triés par date : le purgé (mars), puis Alice, Sara, Alice annulé.
    assertThat(rows.get(0))
        .isEqualTo(
            "| 02/03/2026 | 10:00–10:50 | 50 min | En cabinet | Prévu | Invité purgé |  | non"
                + " |  |  |  |");
    assertThat(rows.get(1))
        .isEqualTo(
            "| 03/12/2026 | 09:00–09:50 | 50 min | En cabinet | Prévu | Alice Martin |  | non"
                + " | Apporter le bilan |  |  |");
    assertThat(rows.get(2))
        .isEqualTo(
            "| 04/12/2026 | 14:00–14:50 | 50 min | En cabinet | Prévu | Sara Benali |"
                + " +33 6 12 34 56 78 | non |  |  |  |");
    assertThat(rows.get(3))
        .isEqualTo(
            "| 10/12/2026 | 08:00–08:50 | 50 min | En cabinet | Annulé | Alice Martin |  | non"
                + " |  | Empêchement du patient | 20/11/2026 07:30 |");
    // Aucune trace de l'invité purgé ni de l'autre praticien.
    assertThat(md)
        .doesNotContain("Durand")
        .doesNotContain("01 02 03 04 05")
        .doesNotContain("Karim")
        .doesNotContain("Secret")
        .doesNotContain("Inconnu");

    // L'autre praticien n'a que ses deux rendez-vous.
    assertThat(tableRows(rendezVous(other))).hasSize(2);
  }

  @Test
  void seriesAreFlagged() throws Exception {
    clock.set(Instant.parse("2026-11-15T08:00:00Z"));
    patientAppointment(
        doctor,
        alice,
        Instant.parse("2026-12-01T09:00:00Z"),
        ",\"recurrence\":{\"everyWeeks\":1,\"count\":2}");
    List<String> rows = tableRows(rendezVous(doctor));
    assertThat(rows).hasSize(2);
    assertThat(rows).allSatisfy(r -> assertThat(r).contains("| oui |"));
  }

  @Test
  void practitionerWithoutAppointmentsGetsHeaderAndEmptyLine() throws Exception {
    String md = rendezVous(doctor);
    assertThat(md).startsWith("# Vos rendez-vous");
    assertThat(md).contains("Aucun rendez-vous.");
    assertThat(md.lines().filter(l -> l.startsWith("|"))).isEmpty();
  }
}
