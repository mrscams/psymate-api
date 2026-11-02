package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 202 : mot de passe correct, la 2FA est active et le code manque. */
@Schema(
    description =
        "Réponse 202 : le mot de passe est correct, la double authentification est active et le"
            + " code manque. Renvoyer la même requête avec le champ otp.")
public record OtpRequiredResponse(
    @Schema(description = "Toujours true.", example = "true") boolean otpRequired) {}
