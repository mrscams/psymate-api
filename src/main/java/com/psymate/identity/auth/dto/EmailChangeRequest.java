package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(
    description =
        "Changement d'adresse e-mail. Sans session (compte non confirmé) : email, password et"
            + " newEmail. Avec une session (compte confirmé) : password et newEmail seulement, le"
            + " compte est celui de la session et email est ignoré. Le mot de passe prouve"
            + " l'identité de l'appelant.")
public record EmailChangeRequest(
    @Schema(
            description =
                "Adresse e-mail actuelle du compte (254 caractères au maximum). Obligatoire sans"
                    + " session ; ignorée avec une session.",
            example = "camille.martin@example.org")
        @Size(max = 254)
        String email,
    @Schema(description = "Mot de passe actuel du compte (200 caractères au maximum).")
        @NotBlank
        @Size(max = 200)
        String password,
    @Schema(
            description =
                "Nouvelle adresse e-mail qui recevra le lien de confirmation (254 caractères au"
                    + " maximum). L'adresse actuelle ne change qu'à la confirmation.",
            example = "camille.martin@example.org")
        @NotBlank
        @Email
        @Size(max = 254)
        String newEmail) {}
