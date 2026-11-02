package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(
    description =
        "Détail d'un compte : mêmes champs que dans la liste, plus l'historique des décisions et"
            + " des recours.")
public record AccountDetailDto(
    @Schema(
            description = "Identifiant du compte.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID id,
    @Schema(description = "Nom d'affichage.", example = "Claire Martin") String displayName,
    @Schema(description = "Adresse e-mail de connexion.", example = "claire.martin@example.org")
        String email,
    @Schema(
            description = "Profession déclarée.",
            allowableValues = {"psychologist", "psychiatrist", "coach"})
        String profession,
    @Schema(
            description = "Ville du cabinet ; null si aucune adresse n'a été renseignée.",
            example = "Lyon")
        String city,
    @Schema(
            description = "Rôle du compte : practitioner ou admin.",
            allowableValues = {"practitioner", "admin"})
        String role,
    @Schema(description = "État de vérification du compte.") VerificationDto verification,
    @Schema(description = "Vrai si la double authentification est activée sur le compte.")
        boolean twoFactorEnabled,
    @Schema(
            description = "Date-heure UTC (ISO 8601) de création du compte.",
            example = "2026-03-10T14:00:00Z")
        Instant createdAt,
    @Schema(
            description =
                "Date-heure UTC (ISO 8601) de la dernière connexion ; null si le compte ne s'est"
                    + " jamais connecté.",
            example = "2026-03-20T07:45:00Z")
        Instant lastLoginAt,
    @Schema(
            description =
                "Date-heure UTC (ISO 8601) de la suspension ou du refus ; null si le compte n'est"
                    + " ni suspendu ni refusé (effacée à la réactivation).",
            example = "2026-04-02T10:00:00Z")
        Instant suspendedAt,
    @Schema(
            description =
                "Date-heure UTC (ISO 8601) de la purge planifiée du compte : 30 jours après le"
                    + " refus du dossier ; null sinon (effacée à la réactivation).",
            example = "2026-05-02T10:00:00Z")
        Instant purgeAt,
    @Schema(
            description =
                "Décisions prises sur ce compte (vérification, suspension, réactivation,"
                    + " recours), de la plus récente à la plus ancienne.")
        List<DecisionItemDto> decisions,
    @Schema(description = "Recours déposés par le praticien, du plus récent au plus ancien.")
        List<AppealSummaryDto> appeals) {}
