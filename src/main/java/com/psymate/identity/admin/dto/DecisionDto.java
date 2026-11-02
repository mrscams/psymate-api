package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Schema(
    name = "AdminDecisionDto",
    description = "Décision enregistrée sur un dossier, avec le nouvel état de vérification.")
public record DecisionDto(
    @Schema(
            description = "Identifiant de la décision créée.",
            example = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d")
        UUID id,
    @Schema(
            description = "Issue enregistrée.",
            allowableValues = {"approved", "needs_more", "rejected"})
        String kind,
    @Schema(
            description =
                "Message au praticien enregistré (blancs de bord retirés) ; null si absent.")
        String messageToPractitioner,
    @Schema(description = "Note interne enregistrée (blancs de bord retirés) ; null si absente.")
        String internalNote,
    @Schema(description = "Liste de contrôle enregistrée ; objet vide s'il n'y en avait pas.")
        Map<String, Object> checklist,
    @Schema(
            description = "Date-heure UTC (ISO 8601) de la décision.",
            example = "2026-03-15T08:00:00Z")
        Instant decidedAt,
    @Schema(
            description = "Identifiant de l'administrateur qui a décidé.",
            example = "5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f")
        UUID adminId,
    @Schema(
            description =
                "État de vérification du compte après la décision : approved, needs_more ou"
                    + " rejected.",
            allowableValues = {"approved", "needs_more", "rejected"})
        String verificationStatus) {}
