package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Changement de mot de passe public (compte de bootstrap) ; otp seulement si la 2FA est active. */
@Schema(
    description =
        "Changement de mot de passe sans session : e-mail et mot de passe actuel prouvent"
            + " l'identité (compte de bootstrap à mot de passe à usage unique, ou changement"
            + " volontaire). otp seulement si la 2FA est active.")
public record PasswordChangeRequest(
    @Schema(
            description = "Adresse e-mail du compte (254 caractères au maximum).",
            example = "camille.martin@example.org")
        @NotBlank
        @Size(max = 254)
        String email,
    @Schema(description = "Mot de passe actuel (200 caractères au maximum).")
        @NotBlank
        @Size(max = 200)
        String currentPassword,
    @Schema(
            description =
                "Nouveau mot de passe (200 caractères au maximum) : politique de mot de passe,"
                    + " différent de l'actuel (422 PASSWORD_POLICY ou PASSWORD_UNCHANGED).")
        @NotBlank
        @Size(max = 200)
        String newPassword,
    @Schema(
            description =
                "Code TOTP à 6 chiffres ou code de secours (32 caractères au maximum), requis si"
                    + " la 2FA est active ; s'il manque, la réponse est 202 otpRequired.",
            example = "123456")
        @Size(max = 32)
        String otp) {}
