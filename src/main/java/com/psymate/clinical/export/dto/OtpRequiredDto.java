package com.psymate.clinical.export.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 202 : mot de passe correct, 2FA active et code manquant. */
@Schema(
    description =
        "Réponse 202 sans job créé : le mot de passe est correct, la double authentification est"
            + " active et aucun code n'a été fourni. Renvoyer la même requête avec otp.")
public record OtpRequiredDto(
    @Schema(description = "Toujours vrai : un code de double authentification est requis.")
        boolean otpRequired) {}
