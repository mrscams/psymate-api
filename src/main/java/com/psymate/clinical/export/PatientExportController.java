package com.psymate.clinical.export;

import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import com.psymate.shared.problem.ProblemMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RestController
class PatientExportController {

  private final PatientExportService exports;
  private final PatientOwnershipGuard guard;

  PatientExportController(PatientExportService exports, PatientOwnershipGuard guard) {
    this.exports = exports;
    this.guard = guard;
  }

  @GetMapping("/api/patients/{id}/export")
  @Operation(
      operationId = "exportPatient",
      summary =
          "Exporte le dossier du patient en Markdown (md) ou en PDF thème clair (pdf), séances et"
              + " entrées en ordre chronologique ; entrées privées et archivées exclues.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Fichier en pièce jointe (text/markdown ou application/pdf)",
        content = {
          @Content(mediaType = "text/markdown", schema = @Schema(type = "string")),
          @Content(
              mediaType = "application/pdf",
              schema = @Schema(type = "string", format = "binary"))
        }),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "PATIENT_NOT_FOUND",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description = "VALIDATION_FAILED : format absent ou différent de md et pdf",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "500",
        description = "EXPORT_FAILED : génération du PDF impossible",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<byte[]> export(
      @PathVariable UUID id,
      @RequestParam(required = false) String format,
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
