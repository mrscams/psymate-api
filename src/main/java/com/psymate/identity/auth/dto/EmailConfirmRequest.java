package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Jeton de confirmation d'adresse e-mail reçu par e-mail.")
public record EmailConfirmRequest(
    @Schema(
            description =
                "Jeton à usage unique reçu dans l'e-mail de confirmation, valable 24 h (200"
                    + " caractères au maximum).",
            example = "q3F9x0Zb7mYw2eKq5uVtJ1cH8aLrN4dS6pGoXi0TyUE")
        @NotBlank
        @Size(max = 200)
        String token) {}
