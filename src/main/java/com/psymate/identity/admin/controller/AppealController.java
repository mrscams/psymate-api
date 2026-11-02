package com.psymate.identity.admin.controller;

import com.psymate.identity.admin.dto.AppealCreatedDto;
import com.psymate.identity.admin.dto.AppealDecisionRequest;
import com.psymate.identity.admin.dto.AppealItemDto;
import com.psymate.identity.admin.dto.AppealListDto;
import com.psymate.identity.admin.dto.AppealRequest;
import com.psymate.identity.admin.dto.OtpRequiredDto;
import com.psymate.identity.admin.model.AdminException;
import com.psymate.identity.admin.model.AdminException.FieldError;
import com.psymate.identity.admin.service.AppealService;
import com.psymate.identity.api.AuthErrorScope;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.SuspendedAccount;
import com.psymate.identity.auth.service.AppealAuthenticator;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recours du compte refusé ou suspendu (fiche 27, M1-14b, G Q28). Le dépôt est public : un compte
 * suspendu n'a pas de session (le login le refuse en 403 ACCOUNT_SUSPENDED), le praticien prouve
 * donc son identité par e-mail + mot de passe (+ code 2FA), comme pour POST
 * /api/auth/password/change. Les routes /api/admin/appeals sont gardées par AdminTwoFactorFilter.
 * AuthErrorScope rend les erreurs d'authentification (401, 423) avec le format de AuthController.
 */
@Tag(
    name = "Recours",
    description =
        "Recours d'un praticien dont le compte est refusé ou suspendu (dépôt public, identifié par"
            + " e-mail et mot de passe) et examen des recours par un administrateur avec"
            + " double authentification active.")
@RestController
class AppealController implements AuthErrorScope {

  private final AppealAuthenticator authenticator;
  private final AppealService service;

  AppealController(AppealAuthenticator authenticator, AppealService service) {
    this.authenticator = authenticator;
    this.service = service;
  }

  @PostMapping("/api/me/appeal")
  @Operation(
      operationId = "submitAppeal",
      summary = "Dépose le recours d'un compte refusé ou suspendu",
      description =
          """
          Public, sans session : le praticien s'identifie par {email, password, otp?} dans le \
          corps, car son compte refusé ou suspendu ne peut plus se connecter. Le corps est \
          validé en premier (422), sans consommer de tentative. Le compte doit être rejected ou \
          suspended (409 sinon) ; si la 2FA est active et que otp manque, réponse 202 avec \
          otpRequired et rien n'est enregistré. Un seul recours ouvert à la fois et un seul par \
          décision contestée ; 5 échecs en 15 minutes verrouillent le couple e-mail et IP 15 \
          minutes (423). Dépôt inscrit au journal d'administration ; 201 sans en-tête Location.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Recours enregistré, au statut open ; en attente d'un administrateur.",
        content = @Content(schema = @Schema(implementation = AppealCreatedDto.class))),
    @ApiResponse(
        responseCode = "202",
        description =
            "Identifiants corrects, 2FA active et code absent : renvoyer la même requête avec"
                + " otp. Rien n'est enregistré.",
        content = @Content(schema = @Schema(implementation = OtpRequiredDto.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "MALFORMED_REQUEST : corps absent ou JSON illisible (code propre à ce module, à la"
                + " place de MALFORMED_JSON).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description =
            "INVALID_CREDENTIALS : e-mail inconnu, mot de passe incorrect ou code 2FA faux"
                + " (message unique, sans indiquer lequel).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description =
            "EMAIL_NOT_CONFIRMED : mot de passe correct mais l'adresse e-mail n'a jamais été"
                + " confirmée.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "ACCOUNT_NOT_SUSPENDED : identifiants corrects mais le compte n'est ni refusé ni"
                + " suspendu ; APPEAL_NOT_ALLOWED : aucune décision de refus ou de suspension à"
                + " contester ; APPEAL_ALREADY_OPEN : un recours est déjà en attente ;"
                + " APPEAL_ALREADY_USED : la décision contestée a déjà fait l'objet d'un recours"
                + " examiné.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ : REQUIRED"
                + " (email, password, message), TOO_SHORT (message, moins de 20 caractères),"
                + " TOO_LONG (message, plus de 4000 caractères).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "423",
        description =
            "ACCOUNT_LOCKED : 5 échecs d'identification en 15 minutes ; réessayer après"
                + " retryAfterSeconds.",
        headers =
            @Header(
                name = "Retry-After",
                description = "Secondes à attendre avant un nouvel essai (15 minutes au plus)."),
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Object> submit(@RequestBody AppealRequest body, HttpServletRequest request) {
    // 422 avant toute vérification d'identifiants : ne consomme ni verrou ni code 2FA.
    List<FieldError> errors = new ArrayList<>();
    if (body.email() == null || body.email().isBlank()) {
      errors.add(new FieldError("email", "REQUIRED", "admin.error.required"));
    }
    if (body.password() == null || body.password().isEmpty()) {
      errors.add(new FieldError("password", "REQUIRED", "admin.error.required"));
    }
    String message = null;
    try {
      message = AppealService.message(body.message());
    } catch (AdminException e) {
      errors.addAll(e.errors());
    }
    if (!errors.isEmpty()) {
      throw AdminException.fields(errors.toArray(FieldError[]::new));
    }
    Optional<SuspendedAccount> who =
        authenticator.verify(body.email(), body.password(), body.otp(), request.getRemoteAddr());
    if (who.isEmpty()) {
      return ResponseEntity.accepted().body(new OtpRequiredDto(true));
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(who.get(), message));
  }

  @GetMapping("/api/admin/appeals")
  @Operation(
      operationId = "listAdminAppeals",
      summary = "Liste les recours à examiner ou déjà examinés",
      description =
          """
          Administrateur avec 2FA. Filtre status : open (non examinés, par défaut), decided \
          (examinés) ou all. Les recours open sont triés du plus ancien au plus récent (file de \
          traitement), decided et all du plus récent au plus ancien. Pagination par curseur \
          opaque : limit 50 par défaut, bornée à 1..200 sans erreur ; nextCursor est null sur la \
          dernière page.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Page de recours."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ :"
                + " INVALID_STATUS (status, autre que open, decided, all), INVALID_CURSOR (cursor"
                + " illisible).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  AppealListDto list(
      @Parameter(
              description =
                  "Recours à lister : open (défaut), decided ou all. Autre valeur : 422"
                      + " INVALID_STATUS.",
              schema =
                  @Schema(
                      allowableValues = {"open", "decided", "all"},
                      defaultValue = "open"),
              example = "open")
          @RequestParam(required = false)
          String status,
      @Parameter(
              description =
                  "Curseur opaque (nextCursor de la page précédente, à renvoyer tel quel) ;"
                      + " absent pour la première page. Illisible : 422 INVALID_CURSOR.")
          @RequestParam(required = false)
          String cursor,
      @Parameter(
              description =
                  "Nombre maximal de recours par page : 50 par défaut, ramené dans l'intervalle"
                      + " 1 à 200 sans erreur.",
              schema = @Schema(type = "integer", format = "int32", defaultValue = "50"),
              example = "50")
          @RequestParam(required = false)
          Integer limit) {
    return service.list(status, cursor, limit);
  }

  @PostMapping("/api/admin/appeals/{id}/decision")
  @Operation(
      operationId = "decideAdminAppeal",
      summary = "Accepte ou écarte un recours",
      description =
          """
          Administrateur avec 2FA, différent de l'auteur de la décision contestée. Le corps est \
          validé en premier (422). accepted : le compte est réactivé comme par la réactivation \
          manuelle (statut d'avant la suspension, ou docs_pending pour un dossier refusé), \
          décision appeal_accepted au journal, un seul e-mail. dismissed : le compte reste \
          inchangé, décision appeal_rejected, e-mail de réponse. Dans les deux cas la réponse \
          est transmise au praticien, l'e-mail part de façon asynchrone après l'enregistrement, \
          et le recours est clos : il ne peut plus être décidé ni redéposé pour la même décision.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Recours examiné, avec son issue."),
    @ApiResponse(
        responseCode = "400",
        description =
            "MALFORMED_REQUEST : corps absent ou JSON illisible (code propre à ce module).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "APPEAL_NOT_FOUND : aucun recours avec cet identifiant.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "APPEAL_ALREADY_DECIDED : le recours a déjà été examiné ; APPEAL_SELF_REVIEW :"
                + " l'administrateur est l'auteur de la décision contestée ;"
                + " APPEAL_ACCOUNT_NOT_REACTIVABLE : outcome accepted alors que le compte n'est"
                + " plus suspended ni rejected.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ :"
                + " INVALID_OUTCOME (outcome, autre que accepted ou dismissed), REQUIRED"
                + " (messageToPractitioner), TOO_LONG (messageToPractitioner, plus de 4000"
                + " caractères). Contrôlé avant la recherche du recours.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  AppealItemDto decide(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @Parameter(
              description = "Identifiant du recours.",
              example = "7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b")
          @PathVariable
          UUID id,
      @RequestBody AppealDecisionRequest body,
      HttpServletRequest request) {
    return service.decide(admin, id, body, request.getRemoteAddr());
  }
}
