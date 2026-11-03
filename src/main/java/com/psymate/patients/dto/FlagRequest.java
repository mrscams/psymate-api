package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Drapeau à poser sur un patient (à la création ou via POST /flags).")
public record FlagRequest(
    @Schema(
            description =
                "Nature du drapeau : watch = à surveiller, risk = risque, shared_care = suivi"
                    + " partagé. Toute autre valeur est refusée (422 INVALID_VALUE), l'absence aussi"
                    + " (422 REQUIRED).",
            allowableValues = {"watch", "risk", "shared_care"},
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "risk")
        String kind,
    @Schema(
            description =
                "Raison libre ; obligatoire (non vide) pour watch, facultative pour risk et"
                    + " shared_care.",
            example = "Idées noires évoquées en séance")
        String reason) {}
