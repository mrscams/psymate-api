package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Confirmation de la désactivation de la 2FA.")
public record TwoFactorDisableRequest(
    @Schema(description = "Mot de passe actuel du compte (200 caractères au maximum).")
        @NotBlank
        @Size(max = 200)
        String password,
    @Schema(
            description =
                "Code TOTP à 6 chiffres ou code de secours non utilisé (32 caractères au"
                    + " maximum).",
            example = "123456")
        @NotBlank
        @Size(max = 32)
        String code) {}
