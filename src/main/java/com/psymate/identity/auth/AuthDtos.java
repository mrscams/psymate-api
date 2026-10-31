package com.psymate.identity.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** DTO de l'API d'authentification. */
final class AuthDtos {

  private AuthDtos() {}

  record SignupRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 200) String password,
      @NotBlank @Size(max = 100) String firstName,
      @NotBlank @Size(max = 100) String lastName,
      @NotBlank @Pattern(regexp = "psychologist|psychiatrist|coach") String profession) {}

  record SignupResponse(UUID id, String email, String displayName, String verificationStatus) {}

  record LoginRequest(
      @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(max = 200) String password,
      boolean rememberMe) {}

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
