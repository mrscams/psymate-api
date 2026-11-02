package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    description =
        "Correction de l'adresse e-mail d'un compte non confirmé ; le mot de passe prouve"
            + " l'identité de l'appelant (aucune session).")
public record EmailChangeRequest(
    @Schema(
            description = "Adresse e-mail actuelle du compte (254 caractères au maximum).",
            example = "camille.martin@example.org")
        @NotBlank
        @Size(max = 254)
        String email,
    @Schema(description = "Mot de passe actuel du compte (200 caractères au maximum).")
        @NotBlank
        @Size(max = 200)
        String password,
    @Schema(
            description =
                "Nouvelle adresse e-mail qui recevra le lien de confirmation (254 caractères au"
                    + " maximum).",
            example = "camille.martin@example.org")
        @NotBlank
        @Email
        @Size(max = 254)
        String newEmail) {}
