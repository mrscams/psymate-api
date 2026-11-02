package com.psymate.clinical.export.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.clinical.export.ExportTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M6-05 : export d'un dossier patient en Markdown et en PDF (F14). Critères : PDF ouvrable, ordre
 * chronologique, entrées privées et archivées exclues (A4.7), seule la version courante, garde de
 * propriété, format validé.
 */
class ExportIT extends ExportTestSupport {

  private static final Instant BASE = Instant.parse("2026-09-01T09:00:00Z");

  private UUID me;
  private UUID patient;

  @BeforeEach
  void setUp() {
    me = account("Dr Export", "approved");
    patient = patient(me, "Élodie Dupont-Ñoël", LocalDate.of(1990, 4, 2));
  }

  // ---- helpers

  private String markdown(UUID patientId) throws Exception {
    return body(
        get("/api/patients/" + patientId + "/export?format=md", as(me)).andExpect(status().isOk()));
  }

  private byte[] pdfBytes(UUID patientId) throws Exception {
    return get("/api/patients/" + patientId + "/export?format=pdf", as(me))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsByteArray();
  }

  private static String pdfText(byte[] pdf) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
    }
  }

  private static int pageCount(byte[] pdf) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      return doc.getNumberOfPages();
    }
  }

  private static String noteJson(String title, String body, String occurredFrom) {
    return """
        {"type":"note","title":"%s","body":"%s",
         "occurred":{"from":"%s","precision":"exact"}}
        """
        .formatted(title, body, occurredFrom);
  }

  /** Jeu de base : trois séances créées dans le désordre, des entrées de tous types. */
  private void threeSessionsOutOfOrder() throws Exception {
    tagTravail = createTag(me, "travail");

    // Séance A : la plus récente, créée en premier.
    String a = startSession(me, patient, BASE.plus(Duration.ofDays(10)));
    createEntry(me, patient, a, noteJson("Entrée A2 tardive", "Texte A2", "2026-09-11T09:40:00Z"));
    createEntry(me, patient, a, noteJson("Entrée A1 précoce", "Texte A1", "2026-09-11T09:10:00Z"));
    closeSession(me, a, "Compte rendu séance A");

    // Séance B : la plus ancienne, avec un ressenti, un médicament, des liens et un tag.
    String b = startSession(me, patient, BASE);
    String event =
        createEntry(
            me,
            patient,
            b,
            """
            {"type":"event","title":"Dispute avec le chef","body":"Discussion tendue.",
             "tags":["travail"],
             "occurred":{"from":"2026-09-01T09:05:00Z","precision":"day"}}
            """);
    createEntry(
        me,
        patient,
        b,
        """
        {"type":"feeling","body":"Boule au ventre",
         "occurred":{"from":"2026-09-01T09:20:00Z","precision":"exact"},
         "feeling":{"emotionCode":"anxiety","intensity":8},
         "links":[{"targetId":"%s","linkType":"triggers","direction":"incoming"}]}
        """
            .formatted(event));
    createEntry(
        me,
        patient,
        b,
        """
        {"type":"medication","title":"Traitement",
         "occurred":{"from":"2026-10-15T09:30:00Z","precision":"month"},
         "medication":{"molecule":"sertraline","dosage":"50 mg","frequency":"matin",
                       "periodFrom":"2026-08-05"}}
        """);
    closeSession(me, b, "Compte rendu séance B");

    // Séance C : au milieu.
    String c = startSession(me, patient, BASE.plus(Duration.ofDays(5)));
    createEntry(me, patient, c, noteJson("Entrée C1", "Texte C1", "2026-09-06T09:10:00Z"));
    closeSession(me, c, "Compte rendu séance C");
  }

  private String tagTravail;

  // ---- 1. Markdown : en-tête, ordre chronologique, détails

  @Test
  void markdownHasHeaderChronologicalSessionsAndEntryDetails() throws Exception {
    threeSessionsOutOfOrder();
    String md = markdown(patient);

    // En-tête : nom, naissance, âge, date d'export, comptes, jamais de coordonnées.
    assertThat(md).contains("Élodie Dupont-Ñoël");
    assertThat(md).contains("Né(e) le 02/04/1990");
    assertThat(md)
        .contains(
            "Date de l'export : "
                + DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    .format(clock.instant().atOffset(ZoneOffset.UTC)));
    assertThat(md).contains("3 séance(s) et 6 entrée(s) exportée(s)");
    assertThat(md).contains("usage professionnel");
    assertThat(md).doesNotContain("0612345678").doesNotContain("contact-secret");

    // Les séances sortent dans l'ordre chronologique croissant, pas celui de la création.
    int b = md.indexOf("Séance du 01/09/2026 09:00");
    int c = md.indexOf("Séance du 06/09/2026 09:00");
    int a = md.indexOf("Séance du 11/09/2026 09:00");
    assertThat(b).isPositive();
    assertThat(b).isLessThan(c);
    assertThat(c).isLessThan(a);

    // Chaque entrée est sous sa séance, en ordre chronologique.
    int a1 = md.indexOf("Entrée A1 précoce");
    int a2 = md.indexOf("Entrée A2 tardive");
    int c1 = md.indexOf("Entrée C1");
    assertThat(a).isLessThan(a1);
    assertThat(a1).isLessThan(a2);
    assertThat(c).isLessThan(c1);
    assertThat(c1).isLessThan(a);
    int event = md.indexOf("Dispute avec le chef");
    int feeling = md.indexOf("Boule au ventre");
    int medication = md.indexOf("sertraline");
    assertThat(b).isLessThan(event);
    assertThat(event).isLessThan(feeling);
    assertThat(feeling).isLessThan(medication);
    assertThat(medication).isLessThan(c);

    // Compte rendu de séance, durée et modalité.
    assertThat(md).contains("Compte rendu séance A").contains("En cabinet").contains("50 min");

    // Date selon la précision : exact avec l'heure, jour sans heure, mois en toutes lettres.
    assertThat(md).contains("Note · 11/09/2026 09:10");
    assertThat(md).contains("Événement · 01/09/2026");
    assertThat(md).doesNotContain("Événement · 01/09/2026 09");
    // Précision mois : occurred_from est ramené au 1er du mois à 00:00 (TimeSpan.resolve), d'où
    // un mois postérieur ici, sinon l'entrée serait à égalité avec l'événement du 1er septembre.
    assertThat(md).contains("Médicament · octobre 2026");

    // Émotion et intensité (libellé du catalogue), médicament, tag.
    assertThat(md).contains("Émotion : Anxiété, intensité 8/10");
    assertThat(md).contains("Médicament : sertraline · 50 mg · matin · depuis le 05/08/2026");
    assertThat(md).contains("Tags : travail");

    // Liens en phrases, avec le libellé inverse du côté de la cible.
    assertThat(md).contains("déclenche : Boule au ventre");
    assertThat(md).contains("déclenché par : Dispute avec le chef");
  }

  // ---- 2. PDF ouvrable

  @Test
  void pdfOpensWithNameEntryTitleAndRunningHeader() throws Exception {
    threeSessionsOutOfOrder();
    MvcResult result =
        get("/api/patients/" + patient + "/export?format=pdf", as(me))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();
    byte[] pdf = result.getResponse().getContentAsByteArray();

    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    assertThat(pageCount(pdf)).isPositive();
    String text = pdfText(pdf);
    assertThat(text).contains("Élodie Dupont-Ñoël");
    assertThat(text).contains("Entrée A1 précoce");
    assertThat(text).contains("usage professionnel");
    assertThat(text).contains("Export PsyMate du");
    assertThat(text).contains("Page 1 / ");
    assertThat(text.indexOf("Séance du 01/09/2026"))
        .isLessThan(text.indexOf("Séance du 11/09/2026"));
  }

  // ---- 3. confidentialité

  @Test
  void privateArchivedAndOldVersionsNeverLeakIntoMarkdownNorPdf() throws Exception {
    String s = startSession(me, patient, BASE);
    createEntry(
        me,
        patient,
        s,
        noteJson("Titre clinique visible", "Corps clinique visible", "2026-09-01T09:10:00Z"));
    createEntry(
        me,
        patient,
        s,
        """
        {"type":"note","title":"Titre secret ZX","body":"Corps secret ZX","visibility":"private"}
        """);
    String archived =
        createEntry(
            me,
            patient,
            s,
            noteJson("Titre archivé AR", "Corps archivé AR", "2026-09-01T09:11:00Z"));
    delete("/api/entries/" + archived).andExpect(status().isNoContent());

    // Corrigée après la fenêtre de grâce : une version 2, la version 1 ne doit pas sortir.
    String corrected =
        createEntry(
            me, patient, s, noteJson("Ancien titre Q", "Ancien corps Q", "2026-09-01T09:12:00Z"));
    clock.advance(Duration.ofHours(2));
    patch(
            "/api/entries/" + corrected,
            "{\"title\":\"Nouveau titre Q\",\"body\":\"Nouveau corps Q\"}",
            as(me))
        .andExpect(status().isOk());
    assertThat(count("select count(*) from entry_version where entry_id = ?::uuid", corrected))
        .isEqualTo(2);

    // Clinique puis passée en privée par correction : disparaît avec toutes ses versions.
    String turned =
        createEntry(
            me, patient, s, noteJson("Titre bascule P", "Corps bascule P", "2026-09-01T09:13:00Z"));
    clock.advance(Duration.ofHours(2));
    patch(
            "/api/entries/" + turned,
            "{\"title\":\"Titre bascule P2\",\"visibility\":\"private\"}",
            as(me))
        .andExpect(status().isOk());

    // Une entrée clinique liée à une entrée privée : le lien est omis, le titre privé aussi.
    String privateTarget =
        createEntry(
            me,
            patient,
            s,
            """
            {"type":"event","title":"Cible privée LK","visibility":"private"}
            """);
    createEntry(
        me,
        patient,
        s,
        """
        {"type":"feeling","title":"Ressenti lié",
         "feeling":{"emotionCode":"anxiety","intensity":3},
         "links":[{"targetId":"%s","linkType":"triggers","direction":"incoming"}]}
        """
            .formatted(privateTarget));
    assertThat(count("select count(*) from link where status = 'accepted'")).isPositive();

    String md = markdown(patient);
    String pdf = pdfText(pdfBytes(patient));
    for (String text : new String[] {md, pdf}) {
      assertThat(text).contains("Titre clinique visible").contains("Nouveau titre Q");
      assertThat(text).contains("Ressenti lié");
      for (String hidden :
          new String[] {
            "Titre secret ZX",
            "Corps secret ZX",
            "Titre archivé AR",
            "Corps archivé AR",
            "Ancien titre Q",
            "Ancien corps Q",
            "Titre bascule P",
            "Corps bascule P",
            "Cible privée LK"
          }) {
        assertThat(text).as("absent de l'export : " + hidden).doesNotContain(hidden);
      }
      assertThat(text).doesNotContain("déclenché par");
    }
    assertThat(md).contains("Nouveau corps Q");
  }

  // ---- 4. garde de propriété et validation

  @Test
  void anotherPractitionersPatientIs403AndUnknownIs404() throws Exception {
    UUID other = account("Dr Autre", "approved");
    get("/api/patients/" + patient + "/export?format=md", as(other))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("PATIENT_FORBIDDEN"));
    get("/api/patients/" + UUID.randomUUID() + "/export?format=md", as(me))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
  }

  @Test
  void invalidOrMissingFormatIs422WithFieldError() throws Exception {
    for (String query : new String[] {"", "?format=docx", "?format="}) {
      get("/api/patients/" + patient + "/export" + query, as(me))
          .andExpect(status().isUnprocessableContent())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[0].field").value("format"))
          .andExpect(jsonPath("$.errors[0].code").value("INVALID_VALUE"))
          .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }
    // Garde d'abord : un patient d'un autre praticien reste 403 même avec un format invalide.
    UUID other = account("Dr Autre", "approved");
    get("/api/patients/" + patient + "/export?format=docx", as(other))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedRequestIs401() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/patients/" + patient + "/export?format=md"))
        .andExpect(status().isUnauthorized());
  }

  // ---- 5. en-têtes de téléchargement

  @Test
  void bothFormatsAreServedAsAttachments() throws Exception {
    String day =
        DateTimeFormatter.BASIC_ISO_DATE.format(
            clock.instant().atOffset(ZoneOffset.UTC).toLocalDate());
    get("/api/patients/" + patient + "/export?format=md", as(me))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", startsWith("text/markdown")))
        .andExpect(header().string("Content-Type", containsString("charset=utf-8")))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"export-elodie-dupont-noel-" + day + ".md\""));
    get("/api/patients/" + patient + "/export?format=pdf", as(me))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"export-elodie-dupont-noel-" + day + ".pdf\""));
  }

  @Test
  void emptyRecordStillProducesAReadableDocument() throws Exception {
    UUID empty = patient(me, "Patient vide", null);
    String md = markdown(empty);
    assertThat(md).contains("Patient vide").contains("Date de naissance non renseignée");
    assertThat(md).contains("Aucune séance ni entrée exportable.");
    assertThat(pageCount(pdfBytes(empty))).isEqualTo(1);
  }

  private org.springframework.test.web.servlet.ResultActions delete(String url) throws Exception {
    return send(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url),
        null,
        as(me));
  }
}
