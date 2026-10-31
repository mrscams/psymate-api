package com.psymate.identity.auth;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.auth.AuthDtos.RecoveryCodesResponse;
import com.psymate.identity.auth.AuthDtos.TwoFactorDisableRequest;
import com.psymate.identity.auth.AuthDtos.TwoFactorEnableResponse;
import com.psymate.identity.auth.AuthDtos.TwoFactorVerifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Double authentification TOTP du praticien connecté (F33, UC-19). */
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
      summary = "Génère le secret TOTP (en attente) et l'URI otpauth:// du QR code")
  TwoFactorEnableResponse enable(@AuthenticationPrincipal CurrentPractitioner current) {
    TwoFactorService.Enrollment e = service.enable(current.id());
    return new TwoFactorEnableResponse(e.secret(), e.otpauthUri());
  }

  @PostMapping("/verify")
  @Operation(
      operationId = "verifyTwoFactor",
      summary = "Vérifie le premier code, active la 2FA et renvoie 10 codes de secours (une fois)")
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
      summary = "Désactive la 2FA (mot de passe et code TOTP ou de secours)")
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
      summary = "Remplace les codes de secours par 10 nouveaux (anciens invalidés)")
  RecoveryCodesResponse regenerate(
      @AuthenticationPrincipal CurrentPractitioner current, HttpServletRequest request) {
    return new RecoveryCodesResponse(
        service.regenerateRecoveryCodes(current.id(), request.getRemoteAddr()));
  }
}
