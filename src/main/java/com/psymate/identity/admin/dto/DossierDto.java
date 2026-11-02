package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(
    description =
        "Dossier complet d'un praticien pour la vérification : identité, coordonnées,"
            + " justificatifs et historique des décisions. Aucune donnée patient.")
public record DossierDto(
    @Schema(
            description = "Identifiant du praticien.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID practitionerId,
    @Schema(description = "Nom d'affichage.", example = "Claire Martin") String displayName,
    @Schema(description = "Prénom.", example = "Claire") String firstName,
    @Schema(description = "Nom de famille.", example = "Martin") String lastName,
    @Schema(description = "Adresse e-mail de connexion.", example = "claire.martin@example.org")
        String email,
    @Schema(
            description = "Profession déclarée.",
            allowableValues = {"psychologist", "psychiatrist", "coach"})
        String profession,
    @Schema(description = "Langue de l'interface du praticien (code ISO 639-1).", example = "fr")
        String locale,
    @Schema(
            description =
                "État de vérification du compte : email_pending, docs_pending, under_review,"
                    + " approved, needs_more, rejected, suspended ou closed.",
            allowableValues = {
              "email_pending",
              "docs_pending",
              "under_review",
              "approved",
              "needs_more",
              "rejected",
              "suspended",
              "closed"
            })
        String verificationStatus,
    @Schema(
            description = "Date-heure UTC (ISO 8601) de création du compte.",
            example = "2026-03-10T14:00:00Z")
        Instant createdAt,
    @Schema(
            description =
                "Date-heure UTC (ISO 8601) de confirmation de l'e-mail ; null si non confirmé.",
            example = "2026-03-10T14:05:00Z")
        Instant emailConfirmedAt,
    @Schema(
            description =
                "Date-heure UTC (ISO 8601) de la dernière soumission du dossier ; null si"
                    + " aucune soumission n'est journalisée.",
            example = "2026-03-14T09:30:00Z")
        Instant submittedAt,
    @Schema(
            description =
                "Vrai si le dossier est under_review depuis plus de 2 jours ouvrés après"
                    + " submittedAt.")
        boolean overdue,
    @Schema(
            description =
                "Date-heure UTC (ISO 8601) de la dernière approbation ; null si le dossier n'a"
                    + " jamais été approuvé.",
            example = "2026-03-15T08:00:00Z")
        Instant verifiedAt,
    @Schema(description = "Coordonnées professionnelles.") ContactDto contact,
    @Schema(
            description =
                "Justificatifs de vérification non retirés par le praticien, du plus ancien au"
                    + " plus récent.")
        List<DocumentDto> documents,
    @Schema(description = "Historique des décisions sur ce compte, du plus récent au plus ancien.")
        List<DecisionItemDto> decisions) {}
