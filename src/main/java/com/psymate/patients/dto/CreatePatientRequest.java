package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "Création d'un patient dans le cabinet du praticien connecté.")
public record CreatePatientRequest(
    @Schema(
            description =
                "Nom affiché ; obligatoire (422 REQUIRED), non vide après suppression des espaces,"
                    + " 120 caractères au plus (422 TOO_LONG).",
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 120,
            example = "Marie D.")
        String displayName,
    @Schema(
            description =
                "Prénom ; vide ou null = non renseigné ; 120 caractères au plus (422 TOO_LONG).",
            maxLength = 120,
            example = "Marie")
        String firstName,
    @Schema(
            description =
                "Nom de famille ; vide ou null = non renseigné ; 120 caractères au plus (422"
                    + " TOO_LONG).",
            maxLength = 120,
            example = "Dupont")
        String lastName,
    @Schema(
            description =
                "Date de naissance AAAA-MM-JJ ; doit être passée et de moins de 120 ans"
                    + " (422 INVALID_DATE sinon).",
            example = "1990-04-02")
        LocalDate birthDate,
    @Schema(
            description =
                "Pronom d'adresse : elle, il, iel ou neutre (422 INVALID_VALUE sinon) ; vide ou"
                    + " null = non renseigné.",
            allowableValues = {"elle", "il", "iel", "neutre"},
            example = "elle")
        String pronoun,
    @Schema(
            description = "Téléphone, texte libre ; vide ou null = non renseigné.",
            example = "0102030405")
        String phone,
    @Schema(
            description = "Adresse e-mail, texte libre ; vide ou null = non renseignée.",
            example = "marie.d@example.org")
        String email,
    @Schema(description = "Contexte de vie : objet JSON libre ; null = objet vide.")
        Map<String, Object> context,
    @Schema(description = "Cadre du suivi : objet JSON libre ; null = objet vide.")
        Map<String, Object> followUp,
    @Schema(
            description =
                "Drapeaux à poser dès la création ; un seul par nature (409 FLAG_ALREADY_ACTIVE"
                    + " sinon) ; null = aucun.")
        List<FlagRequest> flags,
    @Schema(
            description =
                "Libellés de tags existants à associer (mêmes règles que PUT"
                    + " /api/patients/{id}/tags) ; null = aucun.")
        List<String> tags) {}
