package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Nouveau mot de passe choisi à partir du lien de réinitialisation.")
public record PasswordResetConfirmRequest(
    @Schema(
            description =
                "Jeton à usage unique reçu par e-mail, valable 30 minutes (200 caractères au"
                    + " maximum).",
            example = "q3F9x0Zb7mYw2eKq5uVtJ1cH8aLrN4dS6pGoXi0TyUE")
        @NotBlank
        @Size(max = 200)
        String token,
    @Schema(
            description =
                "Nouveau mot de passe (200 caractères au maximum), soumis à la politique de mot"
                    + " de passe (422 PASSWORD_POLICY sinon ; le jeton n'est alors pas"
                    + " consommé).")
        @NotBlank
        @Size(max = 200)
        String password) {}
