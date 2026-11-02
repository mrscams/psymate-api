package com.psymate.identity.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(
    description =
        "Ligne de la file de vérification : un dossier professionnel soumis, à traiter par un"
            + " administrateur.")
public record QueueItemDto(
    @Schema(
            description = "Identifiant du praticien dont le dossier est à vérifier.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID practitionerId,
    @Schema(
            description = "Nom d'affichage du praticien (prénom et nom).",
            example = "Claire Martin")
        String displayName,
    @Schema(
            description = "Profession déclarée.",
            allowableValues = {"psychologist", "psychiatrist", "coach"})
        String profession,
    @Schema(
            description = "Ville du cabinet ; null si aucune adresse n'a été renseignée.",
            example = "Lyon")
        String city,
    @Schema(
            description =
                "Nombre de justificatifs de vérification non retirés par le praticien, y compris"
                    + " ceux déjà purgés.",
            example = "2")
        int documentsCount,
    @Schema(
            description =
                "Date-heure UTC (ISO 8601) de la dernière soumission du dossier ; null si"
                    + " aucune soumission n'est journalisée.",
            example = "2026-03-14T09:30:00Z")
        Instant submittedAt,
    @Schema(
            description =
                "Vrai si le dossier est en statut under_review depuis plus de 2 jours ouvrés"
                    + " après submittedAt ; toujours faux pour needs_more ou sans submittedAt.")
        boolean overdue,
    @Schema(
            description =
                "Types de pièces obligatoires absents du dossier (parmi diploma et identity ; un"
                    + " justificatif purgé ne compte pas) ; liste vide si le dossier est"
                    + " complet.")
        List<String> missingHints) {}
