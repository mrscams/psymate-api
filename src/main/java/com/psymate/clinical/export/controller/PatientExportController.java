package com.psymate.clinical.export.controller;

import com.psymate.clinical.export.model.ExportFormat;
import com.psymate.clinical.export.model.ExportedFile;
import com.psymate.clinical.export.service.ExportTexts;
import com.psymate.clinical.export.service.PatientExportService;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import com.psymate.shared.problem.ProblemMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Export d'un dossier patient en Markdown ou en PDF (F14, M6-05). La garde de propriété passe
 * d'abord : 403 si le patient est à un autre praticien, 404 s'il n'existe pas. Les entrées privées
 * et archivées ne sortent jamais (A4.7).
 */
@Tag(
    name = "Export patient",
    description =
        "Export du dossier d'un patient en fichier Markdown ou PDF, à remettre au patient ou à"
            + " archiver.")
@RestController
class PatientExportController {

  private static final String PROBLEM = "application/problem+json";

  private final PatientExportService exports;
  private final PatientOwnershipGuard guard;

  PatientExportController(PatientExportService exports, PatientOwnershipGuard guard) {
    this.exports = exports;
    this.guard = guard;
  }

  @GetMapping("/api/patients/{id}/export")
  @Operation(
      operationId = "exportPatient",
      summary = "Exporte le dossier du patient en Markdown ou en PDF",
      description =
          """
          Session praticien, propriétaire du patient. Le fichier est généré à la volée et renvoyé en \
          pièce jointe (Content-Disposition attachment, nom export-nom-AAAAMMJJ.md ou .pdf). Il \
          contient les séances et les entrées en ordre chronologique, avec le texte de leur version \
          courante. Les entrées privées et archivées n'en sortent jamais, et un lien vers une entrée \
          non exportée est omis. Langue du document : Accept-Language (français par défaut). Le PDF \
          est au thème clair. Aucune pagination.\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Fichier en pièce jointe : text/markdown (md) ou application/pdf (pdf).",
        headers =
            @Header(
                name = "Content-Disposition",
                description =
                    "attachment avec le nom du fichier, export-nom-AAAAMMJJ.md ou .pdf (nom du"
                        + " patient sans accents, en minuscules).",
                schema = @Schema(type = "string")),
        content = {
          @Content(mediaType = "text/markdown", schema = @Schema(type = "string")),
          @Content(
              mediaType = "application/pdf",
              schema = @Schema(type = "string", format = "binary"))
        }),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "PATIENT_NOT_FOUND : patient inexistant.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : format INVALID_VALUE (paramètre absent, vide ou"
                + " différent de md et pdf). La propriété du patient est vérifiée avant le format.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "500",
        description = "EXPORT_FAILED : génération du PDF impossible.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<byte[]> export(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @Parameter(
              description =
                  "Format du fichier : md (Markdown, text/markdown) ou pdf (application/pdf)."
                      + " Obligatoire ; la casse est ignorée.",
              schema = @Schema(allowableValues = {"md", "pdf"}),
              example = "pdf")
          @RequestParam(required = false)
          String format,
      HttpServletRequest request) {
    guard.requireOwned(id);
    Locale locale = ProblemMessages.localeOf(request);
    ExportFormat parsed = parse(format, locale);
    ExportedFile file = exports.export(id, parsed, locale);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.filename()).build().toString())
        .contentType(MediaType.parseMediaType(file.contentType()))
        .body(file.content());
  }

  private static ExportFormat parse(String format, Locale locale) {
    ExportTexts texts = ExportTexts.of(locale);
    if (format == null || format.isBlank()) {
      throw invalid("INVALID_VALUE", texts.get("error.format.required"), texts);
    }
    return ExportFormat.parse(format)
        .orElseThrow(() -> invalid("INVALID_VALUE", texts.get("error.format.invalid"), texts));
  }

  private static ProblemException invalid(String code, String message, ExportTexts texts) {
    return new ProblemException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "VALIDATION_FAILED",
        texts.get("error.validation.title"),
        texts.get("error.validation.detail"),
        List.of(new FieldProblem("format", code, message)));
  }
}
