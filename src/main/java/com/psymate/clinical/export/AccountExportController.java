package com.psymate.clinical.export;

import com.psymate.clinical.export.ExportDtos.AccountExportRequest;
import com.psymate.clinical.export.ExportDtos.ExportRequestedDto;
import com.psymate.clinical.export.ExportDtos.ExportStatusDto;
import com.psymate.clinical.export.ExportDtos.OtpRequiredDto;
import com.psymate.clinical.export.ExportJobService.JobStatus;
import com.psymate.clinical.export.ExportJobService.Requested;
import com.psymate.clinical.export.ExportJobService.ZipFile;
import com.psymate.identity.api.AuthErrorScope;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.SuspendedAccount;
import com.psymate.identity.api.SuspendedAccountAuthenticator;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import com.psymate.shared.problem.ProblemMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Export de tous les patients du compte (fiche 27, G Q18) : {@code POST /api/me/export}, {@code GET
 * /api/me/export/{jobId}} et {@code GET /api/me/export/{jobId}/file}.
 *
 * <p><b>Qui peut exporter.</b> Un compte approuvé connecté, mais aussi un compte refusé ou
 * suspendu. Ce dernier n'a pas de session (le login le refuse en 403 ACCOUNT_SUSPENDED, ses
 * sessions sont révoquées) : comme pour le recours ({@code /api/me/appeal}, M1-14b), il prouve son
 * identité par e-mail + mot de passe (+ code 2FA) dans le corps du POST, via {@link
 * SuspendedAccountAuthenticator}. Les trois routes sont publiques pour la chaîne de sécurité
 * (permitAll) et font elles-mêmes leur contrôle. Ni session ouverte ni cookie ne sont créés.
 *
 * <p><b>Décision d'authentification.</b> Si un principal est présent (compte approuvé connecté), il
 * est utilisé et le corps ignoré. Sinon le corps est obligatoire (422 si e-mail ou mot de passe
 * manquent) ; un compte approuvé qui s'y présente sans session reçoit 409 ACCOUNT_NOT_SUSPENDED (il
 * doit se connecter), un compte clos aussi : la façade ne lève l'export qu'aux comptes refusés ou
 * suspendus. Les erreurs d'authentification (401 INVALID_CREDENTIALS, 423 avec Retry-After, 403
 * EMAIL_NOT_CONFIRMED, 409) sont rendues par le handler d'identity grâce au marqueur {@link
 * AuthErrorScope}.
 *
 * <p><b>Suivi sans session.</b> Le 202 renvoie un {@code statusUrl} qui porte un jeton HMAC de
 * (job, praticien) : le compte suspendu suit son job sans session, sans révéler d'identifiant
 * exploitable. L'URL du fichier est signée séparément, avec son échéance (7 jours).
 *
 * <p><b>404 plutôt que 403.</b> Un job d'un autre praticien, un job inconnu ou un jeton faux
 * donnent tous le même 404 EXPORT_NOT_FOUND : un 403 confirmerait que l'identifiant existe. Seule
 * l'URL du fichier, dont la signature est invalide, répond 403 SIGNED_URL_INVALID (comme les URL
 * signées des justificatifs).
 */
@RestController
class AccountExportController implements AuthErrorScope {

  private final SuspendedAccountAuthenticator authenticator;
  private final ExportJobService jobs;

  AccountExportController(SuspendedAccountAuthenticator authenticator, ExportJobService jobs) {
    this.authenticator = authenticator;
    this.jobs = jobs;
  }

  @PostMapping("/api/me/export")
  @Operation(
      operationId = "requestAccountExport",
      summary =
          "Lance l'export asynchrone de tous les patients du compte (zip chiffré, lien 7 jours) :"
              + " 202 jobId et statusUrl, ou 202 otpRequired. Compte connecté (corps ignoré) ou"
              + " compte refusé/suspendu par e-mail + mot de passe (+ code 2FA) dans le corps.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Job créé (jobId, statusUrl), ou code 2FA requis (otpRequired)",
        content = @Content(schema = @Schema(implementation = ExportRequestedDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "INVALID_CREDENTIALS : identifiants incorrects ou code 2FA faux",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "EMAIL_NOT_CONFIRMED",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "EXPORT_IN_PROGRESS (un seul export en cours) ou ACCOUNT_NOT_SUSPENDED (compte"
                + " approuvé ou clos sans session)",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description = "VALIDATION_FAILED : e-mail ou mot de passe manquants sans session",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "423",
        description = "Verrouillage après 5 échecs (15 min)",
        headers = @Header(name = "Retry-After", description = "Secondes avant nouvel essai"),
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Object> request(
      @RequestBody(required = false) AccountExportRequest body, HttpServletRequest http) {
    ExportTexts texts = ExportTexts.of(ProblemMessages.localeOf(http));
    Optional<CurrentPractitioner> me = CurrentPractitioner.get();
    UUID practitionerId;
    if (me.isPresent()) {
      practitionerId = me.get().id();
    } else {
      AccountExportRequest credentials =
          body == null ? new AccountExportRequest(null, null, null) : body;
      requireCredentials(credentials, texts);
      Optional<SuspendedAccount> who =
          authenticator.verify(
              credentials.email(), credentials.password(), credentials.otp(), http.getRemoteAddr());
      if (who.isEmpty()) {
        return ResponseEntity.accepted().body(new OtpRequiredDto(true));
      }
      practitionerId = who.get().id();
    }
    Requested requested = jobs.request(practitionerId, texts);
    return ResponseEntity.accepted()
        .body(new ExportRequestedDto(requested.jobId(), requested.statusUrl()));
  }

  /** 422 avant toute vérification d'identifiants : ne consomme ni verrou ni code 2FA. */
  private static void requireCredentials(AccountExportRequest body, ExportTexts texts) {
    List<FieldProblem> errors = new ArrayList<>();
    if (body.email() == null || body.email().isBlank()) {
      errors.add(new FieldProblem("email", "REQUIRED", texts.get("error.required")));
    }
    if (body.password() == null || body.password().isEmpty()) {
      errors.add(new FieldProblem("password", "REQUIRED", texts.get("error.required")));
    }
    if (!errors.isEmpty()) {
      throw new ProblemException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "VALIDATION_FAILED",
          texts.get("error.validation.title"),
          texts.get("error.validation.detail"),
          errors);
    }
  }

  @GetMapping("/api/me/export/{jobId}")
  @Operation(
      operationId = "getAccountExport",
      summary =
          "Statut d'un export du compte : praticien connecté propriétaire du job, ou paramètre"
              + " token du statusUrl. downloadUrl seulement quand le job est terminé et non"
              + " expiré.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = ExportStatusDto.class))),
    @ApiResponse(
        responseCode = "404",
        description = "EXPORT_NOT_FOUND : job inconnu, d'un autre praticien, ou jeton invalide",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ExportStatusDto status(
      @PathVariable UUID jobId,
      @RequestParam(required = false) String token,
      HttpServletRequest http) {
    ExportTexts texts = ExportTexts.of(ProblemMessages.localeOf(http));
    JobStatus s =
        jobs.status(jobId, CurrentPractitioner.get().map(CurrentPractitioner::id), token, texts);
    return new ExportStatusDto(
        s.jobId(),
        s.status(),
        s.requestedAt(),
        s.finishedAt(),
        s.expiresAt(),
        s.downloadUrl(),
        s.error());
  }

  @GetMapping("/api/me/export/{jobId}/file")
  @Operation(
      operationId = "downloadAccountExport",
      summary =
          "Télécharge le zip de l'export (URL signée valable 7 jours) : 200 application/zip, 403"
              + " signature invalide, 410 expiré ou purgé.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Zip en pièce jointe : patients.md, puis un .md et un .pdf par patient",
        content =
            @Content(
                mediaType = "application/zip",
                schema = @Schema(type = "string", format = "binary"))),
    @ApiResponse(
        responseCode = "403",
        description = "SIGNED_URL_INVALID",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "410",
        description = "EXPORT_EXPIRED : échéance passée ou fichier purgé",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<byte[]> download(
      @PathVariable UUID jobId,
      @RequestParam(required = false) String exp,
      @RequestParam(required = false) String sig,
      HttpServletRequest http) {
    ExportTexts texts = ExportTexts.of(ProblemMessages.localeOf(http));
    ZipFile file = jobs.file(jobId, exp, sig, texts);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.filename()).build().toString())
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(file.content());
  }
}
