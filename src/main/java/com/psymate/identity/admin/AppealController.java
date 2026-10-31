package com.psymate.identity.admin;

import com.psymate.identity.admin.AdminAccountDtos.AppealCreatedDto;
import com.psymate.identity.admin.AdminAccountDtos.AppealDecisionRequest;
import com.psymate.identity.admin.AdminAccountDtos.AppealItemDto;
import com.psymate.identity.admin.AdminAccountDtos.AppealListDto;
import com.psymate.identity.admin.AdminAccountDtos.AppealRequest;
import com.psymate.identity.admin.AdminAccountDtos.OtpRequiredDto;
import com.psymate.identity.admin.AdminException.FieldError;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.auth.AppealAuthenticator;
import com.psymate.identity.auth.AppealAuthenticator.AppealIdentity;
import com.psymate.identity.auth.AuthErrorScope;
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
      summary =
          "Dépose le recours d'un compte refusé ou suspendu (public : e-mail + mot de passe) : 201,"
              + " 202 code 2FA requis, 401, 403, 409, 422, 423. Un seul recours par décision.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        content = @Content(schema = @Schema(implementation = AppealCreatedDto.class))),
    @ApiResponse(
        responseCode = "202",
        description = "Mot de passe correct, 2FA active et code manquant",
        content = @Content(schema = @Schema(implementation = OtpRequiredDto.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Identifiants incorrects (message unique) ou code 2FA faux",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "ACCOUNT_NOT_SUSPENDED, APPEAL_NOT_ALLOWED, APPEAL_ALREADY_OPEN ou APPEAL_ALREADY_USED",
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
    Optional<AppealIdentity> who =
        authenticator.verify(body.email(), body.password(), body.otp(), request.getRemoteAddr());
    if (who.isEmpty()) {
      return ResponseEntity.accepted().body(new OtpRequiredDto(true));
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(who.get(), message));
  }

  @GetMapping("/api/admin/appeals")
  @Operation(
      operationId = "listAdminAppeals",
      summary =
          "Recours (status open par défaut, decided ou all), pagination par curseur ; les plus"
              + " anciens d'abord pour open")
  AppealListDto list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return service.list(status, cursor, limit);
  }

  @PostMapping("/api/admin/appeals/{id}/decision")
  @Operation(
      operationId = "decideAdminAppeal",
      summary =
          "Accepte ou rejette un recours (accepted réactive le compte) ; l'administrateur doit"
              + " différer de l'auteur de la décision contestée")
  AppealItemDto decide(
      @AuthenticationPrincipal CurrentPractitioner admin,
      @PathVariable UUID id,
      @RequestBody AppealDecisionRequest body,
      HttpServletRequest request) {
    return service.decide(admin, id, body, request.getRemoteAddr());
  }
}
