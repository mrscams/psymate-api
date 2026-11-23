package com.psymate.clinical.export.controller;

import static com.psymate.testsupport.ArabicFallback.ARABIC;
import static com.psymate.testsupport.ArabicFallback.assertAlwaysFrench;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.psymate.clinical.export.ExportTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * G Q102 : l'export d'un dossier demandé en arabe sort en français, Markdown et PDF, textes et
 * libellés de catalogue compris, quels que soient les fichiers de messages. Même configuration
 * qu'ExportIT.
 */
class ExportArabicIT extends ExportTestSupport {

  private UUID me;
  private UUID patient;

  @BeforeEach
  void setUp() throws Exception {
    me = account("Dr Export", "approved");
    patient = patient(me, "Élodie Dupont", LocalDate.of(1990, 4, 2));
    String session = startSession(me, patient, Instant.parse("2026-09-01T09:00:00Z"));
    String event =
        createEntry(
            me,
            patient,
            session,
            """
            {"type":"event","title":"Dispute avec le chef","body":"Discussion tendue.",
             "occurred":{"from":"2026-09-01T09:05:00Z","precision":"day"}}
            """);
    createEntry(
        me,
        patient,
        session,
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
        session,
        """
        {"type":"note","title":"Mois","body":"Texte",
         "occurred":{"from":"2026-10-15T09:30:00Z","precision":"month"}}
        """);
    closeSession(me, session, "Compte rendu");
  }

  private ResultActions export(String format, String language) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.get("/api/patients/" + patient + "/export?format=" + format)
            .header("Accept-Language", language)
            .with(as(me)));
  }

  private static String pdfText(byte[] pdf) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
    }
  }

  @Test
  void arabicMarkdownExportIsTheFrenchOne() throws Exception {
    ResultActions french = export("md", "fr").andExpect(status().isOk());
    assertThat(french.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
        .contains("Émotion : Anxiété")
        .contains("déclenché par : Dispute avec le chef")
        .contains("octobre 2026");
    assertAlwaysFrench(french, export("md", ARABIC));
  }

  @Test
  void arabicPdfExportIsTheFrenchOne() throws Exception {
    byte[] french =
        export("pdf", "fr")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    byte[] arabic =
        export("pdf", ARABIC)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(pdfText(arabic)).isEqualTo(pdfText(french)).contains("Dispute avec le chef");
  }

  @Test
  void arabicExportErrorsAreTheFrenchOnes() throws Exception {
    assertAlwaysFrench(
        export("docx", "fr").andExpect(status().is4xxClientError()), export("docx", ARABIC));
    assertAlwaysFrench(export("", "fr").andExpect(status().is4xxClientError()), export("", ARABIC));
  }
}
