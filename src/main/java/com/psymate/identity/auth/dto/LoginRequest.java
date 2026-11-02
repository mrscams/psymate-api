package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** otp : code TOTP à 6 chiffres ou code de secours ; rememberMe absent = false. */
@Schema(description = "Identifiants de connexion ; otp seulement si la 2FA est active.")
public record LoginRequest(
    @Schema(
            description = "Adresse e-mail du compte (254 caractères au maximum).",
            example = "camille.martin@example.org")
        @NotBlank
        @Size(max = 254)
        String email,
    @Schema(description = "Mot de passe du compte (200 caractères au maximum).")
        @NotBlank
        @Size(max = 200)
        String password,
    @Schema(
            description =
                "Code TOTP à 6 chiffres ou code de secours (32 caractères au maximum). À envoyer"
                    + " seulement si la 2FA est active ; s'il manque, la réponse est 202"
                    + " otpRequired.",
            example = "123456")
        @Size(max = 32)
        String otp,
    @Schema(
            description =
                "true pour une session de 30 jours (cookie persistant) au lieu de 12 h"
                    + " d'inactivité. Absent = false.")
        Boolean rememberMe) {}
