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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(
    name = "Export de compte",
    description =
        "Export asynchrone de tous les patients d'un compte, y compris refusé ou suspendu"
            + " (zip de fichiers Markdown et PDF, lien valable 7 jours).")
@RestController
class AccountExportController implements AuthErrorScope {

  private static final String PROBLEM = "application/problem+json";
  private static final String JOB_ID_EXAMPLE = "5c1d7e2a-8b3f-4a69-b0d4-7e6f5a4b3c2d";

  private final SuspendedAccountAuthenticator authenticator;
  private final ExportJobService jobs;

  AccountExportController(SuspendedAccountAuthenticator authenticator, ExportJobService jobs) {
    this.authenticator = authenticator;
    this.jobs = jobs;
  }

  @PostMapping("/api/me/export")
  @Operation(
      operationId = "requestAccountExport",
      summary = "Lance l'export asynchrone de tous les patients du compte",
      description =
          """
          Public pour la chaîne de sécurité, contrôle fait par la route : compte approuvé connecté \
          (session praticien, corps ignoré), ou compte refusé ou suspendu sans session, qui fournit \
          {email, password, otp?} dans le corps. Un seul export en cours par compte. Réponse 202 \
          asynchrone : jobId et statusUrl à interroger (GET /api/me/export/{jobId}), ou otpRequired \
          sans création de job si la double authentification est active et que otp manque. Le zip \
          (patients.md, puis un .md et un .pdf par patient) est conservé chiffré et téléchargeable \
          7 jours. Verrouillage de 15 minutes après 5 échecs d'authentification (423 Retry-After).\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description =
            "Deux corps possibles : {jobId, statusUrl} quand le job est créé ; {otpRequired: true}"
                + " quand le code de double authentification manque (aucun job créé).",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(oneOf = {ExportRequestedDto.class, OtpRequiredDto.class}))),
    @ApiResponse(
        responseCode = "401",
        description = "INVALID_CREDENTIALS : identifiants incorrects ou code 2FA faux.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "EMAIL_NOT_CONFIRMED : adresse e-mail du compte non confirmée.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "EXPORT_IN_PROGRESS : un export de ce compte est déjà en file ou en cours ;"
                + " ACCOUNT_NOT_SUSPENDED : compte approuvé sans session (se connecter) ou compte"
                + " clos.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : email et password REQUIRED quand il n'y a ni session"
                + " ni identifiants dans le corps.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "423",
        description =
            "ACCOUNT_LOCKED : 5 échecs d'identification en 15 minutes, réessayer après"
                + " Retry-After.",
        headers =
            @Header(
                name = "Retry-After",
                description = "Secondes avant un nouvel essai.",
                schema = @Schema(type = "integer")),
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
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
      summary = "Donne le statut d'un export de compte",
      description =
          """
          Public pour la chaîne de sécurité : praticien connecté propriétaire du job, ou porteur du \
          paramètre token du statusUrl (compte suspendu sans session). À interroger jusqu'à ce que \
          status vaille done ou failed. downloadUrl n'est renseigné que si le job est terminé et le \
          lien non expiré (7 jours). Un job inconnu, d'un autre praticien ou un jeton faux donnent \
          le même 404, pour ne rien révéler.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Statut du job d'export."),
    @ApiResponse(
        responseCode = "404",
        description =
            "EXPORT_NOT_FOUND : job inconnu, d'un autre praticien, ou jeton token absent ou"
                + " invalide.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ExportStatusDto status(
      @Parameter(description = "Identifiant du job d'export.", example = JOB_ID_EXAMPLE)
          @PathVariable
          UUID jobId,
      @Parameter(
              description =
                  "Jeton de suivi porté par le statusUrl, pour un compte sans session. Inutile"
                      + " quand le praticien propriétaire du job est connecté.",
              example = "Zm9vYmFy")
          @RequestParam(required = false)
          String token,
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
      summary = "Télécharge le zip d'un export de compte par son URL signée",
      description =
          """
          Public pour la chaîne de sécurité : l'autorisation est la signature (paramètres exp et \
          sig) de la downloadUrl renvoyée par GET /api/me/export/{jobId}, valable 7 jours. Renvoie \
          le zip déchiffré en pièce jointe (Content-Disposition attachment, nom \
          export-psymate-AAAAMMJJ.zip). Passé l'échéance ou une fois le fichier purgé : 410.\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Zip en pièce jointe : patients.md, puis un .md et un .pdf par patient.",
        headers =
            @Header(
                name = "Content-Disposition",
                description = "attachment avec le nom du fichier, export-psymate-AAAAMMJJ.zip.",
                schema = @Schema(type = "string")),
        content =
            @Content(
                mediaType = "application/zip",
                schema = @Schema(type = "string", format = "binary"))),
    @ApiResponse(
        responseCode = "403",
        description =
            "SIGNED_URL_INVALID : signature ou échéance absente, falsifiée ou illisible, job"
                + " inconnu ou non terminé.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "410",
        description = "EXPORT_EXPIRED : échéance passée ou fichier purgé.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<byte[]> download(
      @Parameter(description = "Identifiant du job d'export.", example = JOB_ID_EXAMPLE)
          @PathVariable
          UUID jobId,
      @Parameter(
              description = "Échéance du lien, en secondes depuis l'epoch UTC, telle que signée.",
              example = "1793649600")
          @RequestParam(required = false)
          String exp,
      @Parameter(
              description = "Signature du lien, telle que renvoyée dans downloadUrl.",
              example = "q3Zk0w")
          @RequestParam(required = false)
          String sig,
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
