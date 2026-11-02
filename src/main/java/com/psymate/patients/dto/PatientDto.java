package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(
    description =
        "Fiche d'un patient du praticien connecté, avec ses drapeaux actifs et ses tags."
            + " Chaque modification par PATCH incrémente currentVersion et archive un"
            + " instantané consultable via GET /api/patients/{id}/versions.")
public record PatientDto(
    @Schema(
            description = "Identifiant du patient.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID id,
    @Schema(
            description =
                "Nom affiché dans toute l'application (obligatoire, espaces de bord retirés).",
            example = "Marie D.")
        String displayName,
    @Schema(description = "Prénom ; null si non renseigné.", example = "Marie") String firstName,
    @Schema(description = "Nom de famille ; null si non renseigné.", example = "Dupont")
        String lastName,
    @Schema(
            description =
                "Date de naissance AAAA-MM-JJ ; null si non renseignée. Passée et de moins de"
                    + " 120 ans.",
            example = "1990-04-02")
        LocalDate birthDate,
    @Schema(description = "Pronom d'adresse libre ; null si non renseigné.", example = "elle")
        String pronoun,
    @Schema(description = "Téléphone, texte libre ; null si non renseigné.", example = "0102030405")
        String phone,
    @Schema(
            description = "Adresse e-mail, texte libre ; null si non renseignée.",
            example = "marie.d@example.org")
        String email,
    @Schema(
            description =
                "Contexte de vie du patient : objet JSON libre dont les clés et valeurs sont"
                    + " définies par le client (par exemple activity). Objet vide si rien n'est"
                    + " renseigné.")
        Map<String, Object> context,
    @Schema(
            description =
                "Cadre du suivi : objet JSON libre dont les clés et valeurs sont définies par le"
                    + " client (par exemple rhythm). Objet vide si rien n'est renseigné.")
        Map<String, Object> followUp,
    @Schema(description = "Drapeaux actifs, du plus ancien au plus récent ; liste vide si aucun.")
        List<FlagDto> flags,
    @Schema(description = "Tags du patient ; liste vide si aucun.") List<TagDto> tags,
    @Schema(
            description =
                "Numéro de la version courante de la fiche : 1 à la création, +1 à chaque"
                    + " modification par PATCH.",
            example = "1")
        int currentVersion,
    @Schema(
            description =
                "Date-heure d'archivage, UTC ISO 8601 ; null tant que le patient est actif.",
            example = "2026-03-14T09:30:00Z")
        OffsetDateTime archivedAt,
    @Schema(description = "Date-heure de création, UTC ISO 8601.", example = "2026-01-05T08:00:00Z")
        OffsetDateTime createdAt,
    @Schema(
            description = "Date-heure de dernière modification, UTC ISO 8601.",
            example = "2026-03-14T09:30:00Z")
        OffsetDateTime updatedAt) {}
