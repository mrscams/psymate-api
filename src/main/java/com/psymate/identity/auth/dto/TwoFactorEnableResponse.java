package com.psymate.identity.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Secret TOTP en attente d'activation, montré une seule fois.")
public record TwoFactorEnableResponse(
    @Schema(
            description =
                "Secret TOTP en Base32, à saisir dans l'application d'authentification si le QR"
                    + " code n'est pas lisible.",
            example = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP")
        String secret,
    @Schema(
            description =
                "URI otpauth:// à encoder en QR code (émetteur PsyMate, SHA1, 6 chiffres, pas de"
                    + " 30 s).",
            example = "otpauth://totp/PsyMate:camille.martin%40example.org?secret=JBSWY3DPEHPK3PXP")
        String otpauthUri) {}
