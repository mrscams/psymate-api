package com.psymate.identity.auth;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.auth.AuthDtos.RecoveryCodesResponse;
import com.psymate.identity.auth.AuthDtos.TwoFactorDisableRequest;
import com.psymate.identity.auth.AuthDtos.TwoFactorEnableResponse;
import com.psymate.identity.auth.AuthDtos.TwoFactorVerifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Double authentification TOTP du praticien connecté (F33, UC-19). Erreurs : problem+json. */
@Tag(
    name = "Double authentification",
    description =
        "Activation, désactivation et codes de secours de la double authentification TOTP du"
            + " praticien connecté.")
@RestController
@RequestMapping("/api/me/2fa")
class TwoFactorController {

  private final TwoFactorService service;

  TwoFactorController(TwoFactorService service) {
    this.service = service;
  }

  @PostMapping("/enable")
  @Operation(
      operationId = "enableTwoFactor",
      summary = "Génère le secret TOTP (en attente) et l'URI otpauth:// du QR code",
      description =
          """
          Session praticien. Première étape d'activation : génère un secret en attente et renvoie \
          le secret Base32 et l'URI otpauth:// du QR code, montrés une seule fois. La 2FA n'est pas \
          active tant que POST /api/me/2fa/verify n'a pas validé un premier code ; rappeler cette \
          route avant la vérification remplace le secret en attente.\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Secret en attente généré ; la 2FA n'est pas encore active."),
    @ApiResponse(
        responseCode = "409",
        description = "TWO_FACTOR_ALREADY_ENABLED : la double authentification est déjà active.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  TwoFactorEnableResponse enable(@AuthenticationPrincipal CurrentPractitioner current) {
    TwoFactorService.Enrollment e = service.enable(current.id());
    return new TwoFactorEnableResponse(e.secret(), e.otpauthUri());
  }

  @PostMapping("/verify")
  @Operation(
      operationId = "verifyTwoFactor",
      summary = "Vérifie le premier code, active la 2FA et renvoie 10 codes de secours (une fois)",
      description =
          """
          Session praticien. Deuxième étape d'activation, après POST /api/me/2fa/enable : valide \
          le code TOTP à 6 chiffres, active la 2FA et renvoie 10 codes de secours en clair, montrés \
          une seule fois. Ce code ne pourra pas être rejoué au login dans la même fenêtre de 30 s. \
          5 codes faux d'affilée verrouillent les essais 15 min (423).\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "2FA activée ; les 10 codes de secours sont renvoyés une seule fois."),
    @ApiResponse(
        responseCode = "409",
        description =
            "TWO_FACTOR_ALREADY_ENABLED : 2FA déjà active ; TWO_FACTOR_NOT_STARTED : aucun secret"
                + " en attente, appeler enable d'abord.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : INVALID_CODE (code TOTP incorrect ou déjà utilisé),"
                + " INVALID_FIELD (code absent ou de plus de 32 caractères).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "423",
        description =
            "ACCOUNT_LOCKED : 5 codes faux, essais verrouillés 15 min ; retryAfterSeconds et"
                + " en-tête Retry-After.",
        headers = @Header(name = "Retry-After", description = "Secondes avant nouvel essai"),
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  RecoveryCodesResponse verify(
      @AuthenticationPrincipal CurrentPractitioner current,
      @Valid @RequestBody TwoFactorVerifyRequest r,
      HttpServletRequest request) {
    return new RecoveryCodesResponse(
        service.verify(current.id(), r.code(), request.getRemoteAddr()));
  }

  @PostMapping("/disable")
  @Operation(
      operationId = "disableTwoFactor",
      summary = "Désactive la 2FA (mot de passe et code TOTP ou de secours)",
      description =
          """
          Session praticien dont la 2FA est active, hors administrateur (la 2FA est obligatoire \
          pour un compte admin : 409 TWO_FACTOR_REQUIRED_FOR_ADMIN, refus avant tout contrôle de \
          code). Exige le mot de passe actuel et un code TOTP ou un code de secours non utilisé. \
          Succès : le secret et tous les codes de secours sont retirés. 5 échecs d'affilée \
          verrouillent les essais 15 min (423).\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "2FA désactivée, codes de secours retirés."),
    @ApiResponse(
        responseCode = "409",
        description =
            "TWO_FACTOR_NOT_ENABLED : la 2FA n'est pas active ; TWO_FACTOR_REQUIRED_FOR_ADMIN :"
                + " un compte administrateur ne peut pas désactiver sa 2FA.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : INVALID_PASSWORD (password), INVALID_CODE (code"
                + " TOTP ou de secours incorrect), INVALID_FIELD (champ absent ou trop long).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "423",
        description =
            "ACCOUNT_LOCKED : 5 échecs, essais verrouillés 15 min ; retryAfterSeconds et en-tête"
                + " Retry-After.",
        headers = @Header(name = "Retry-After", description = "Secondes avant nouvel essai"),
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> disable(
      @AuthenticationPrincipal CurrentPractitioner current,
      @Valid @RequestBody TwoFactorDisableRequest r,
      HttpServletRequest request) {
    service.disable(current.id(), r.password(), r.code(), request.getRemoteAddr());
    return ResponseEntity.noContent().build();
  }

  /** POST et non GET : écriture (les anciens codes sont invalidés). */
  @PostMapping("/recovery-codes/regenerate")
  @Operation(
      operationId = "regenerateRecoveryCodes",
      summary = "Remplace les codes de secours par 10 nouveaux (anciens invalidés)",
      description =
          """
          Session praticien dont la 2FA est active. Invalide tous les codes de secours existants \
          et renvoie 10 nouveaux codes en clair, montrés une seule fois. Aucun mot de passe ni \
          code n'est redemandé.\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Nouveaux codes de secours, renvoyés une seule fois."),
    @ApiResponse(
        responseCode = "409",
        description = "TWO_FACTOR_NOT_ENABLED : la 2FA n'est pas active.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  RecoveryCodesResponse regenerate(
      @AuthenticationPrincipal CurrentPractitioner current, HttpServletRequest request) {
    return new RecoveryCodesResponse(
        service.regenerateRecoveryCodes(current.id(), request.getRemoteAddr()));
  }
}
