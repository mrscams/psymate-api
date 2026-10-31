package com.psymate.identity.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** DTO de l'API d'authentification. */
final class AuthDtos {

  private AuthDtos() {}

  /** Adresse du cabinet ; les champs sont contrôlés par le service (obligatoires sauf visio). */
  record Address(
      @Size(max = 200) String street,
      @Size(max = 20) String postalCode,
      @Size(max = 100) String city) {}

  /** Consentements obligatoires (CGU et politique de confidentialité). */
  record Consents(@NotNull @AssertTrue Boolean terms, @NotNull @AssertTrue Boolean privacy) {}

  record RegisterRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 200) String password,
      @NotBlank @Size(max = 100) String firstName,
      @NotBlank @Size(max = 100) String lastName,
      @NotBlank @Pattern(regexp = "psychologist|psychiatrist|coach") String profession,
      @Size(max = 30) String professionalNumber,
      @NotBlank @Size(max = 30) String phone,
      @Valid Address address,
      Boolean remoteOnly,
      @NotNull @Valid Consents consents) {}

  /** Corps identique que le compte existe déjà ou non (anti-énumération). */
  record RegisterResponse(String status) {}

  record EmailConfirmRequest(@NotBlank @Size(max = 200) String token) {}

  record EmailResendRequest(@NotBlank @Email @Size(max = 254) String email) {}

  record EmailChangeRequest(
      @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(max = 200) String password,
      @NotBlank @Email @Size(max = 254) String newEmail) {}

  record SessionResponse(
      UUID id,
      String email,
      String displayName,
      String profession,
      String verificationStatus,
      boolean emailConfirmed) {}

  /** otp : code TOTP à 6 chiffres ou code de secours ; rememberMe absent = false. */
  record LoginRequest(
      @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(max = 200) String password,
      @Size(max = 32) String otp,
      Boolean rememberMe) {}

  /** 202 : mot de passe correct, la 2FA est active et le code manque. */
  record OtpRequiredResponse(boolean otpRequired) {}

  record TwoFactorEnableResponse(String secret, String otpauthUri) {}

  record TwoFactorVerifyRequest(@NotBlank @Size(max = 32) String code) {}

  record TwoFactorDisableRequest(
      @NotBlank @Size(max = 200) String password, @NotBlank @Size(max = 32) String code) {}

  /** Codes de secours en clair : montrés une seule fois. */
  record RecoveryCodesResponse(java.util.List<String> recoveryCodes) {}

  record RevokedSessionsResponse(int revoked) {}

  record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {}

  record PasswordResetConfirmRequest(
      @NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 200) String password) {}

  record MeResponse(
      UUID id,
      UUID tenantId,
      String email,
      String displayName,
      String profession,
      String verificationStatus) {}
}
