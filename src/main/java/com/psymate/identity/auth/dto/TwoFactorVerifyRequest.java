package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Premier code TOTP produit par l'application d'authentification.")
public record TwoFactorVerifyRequest(
    @Schema(
            description = "Code TOTP à 6 chiffres affiché par l'application (32 caractères max).",
            example = "123456")
        @NotBlank
        @Size(max = 32)
        String code) {}
