package com.psymate.clinical.export.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Corps optionnel de POST /api/me/export pour un compte sans session. */
@Schema(
    description =
        "Identifiants d'un compte refusé ou suspendu, qui n'a pas de session. Ignoré (et"
            + " facultatif) quand le praticien est connecté ; obligatoire sinon (email et"
            + " password REQUIRED).")
public record AccountExportRequest(
    @Schema(
            description = "Adresse e-mail du compte. Obligatoire sans session.",
            example = "dr.martin@example.org")
        String email,
    @Schema(description = "Mot de passe du compte. Obligatoire sans session.") String password,
    @Schema(
            description =
                "Code de double authentification du compte. Absent : si la double"
                    + " authentification est active, la réponse 202 demande le code"
                    + " (otpRequired).",
            example = "123456")
        String otp) {}
