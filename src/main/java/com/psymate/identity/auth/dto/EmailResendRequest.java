package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Demande de renvoi de l'e-mail de confirmation.")
public record EmailResendRequest(
    @Schema(
            description = "Adresse e-mail du compte non confirmé (254 caractères au maximum).",
            example = "camille.martin@example.org")
        @NotBlank
        @Email
        @Size(max = 254)
        String email) {}
