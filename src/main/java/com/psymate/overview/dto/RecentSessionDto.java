package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Séance récente ; seule la plus récente porte le compte rendu entier.")
public record RecentSessionDto(
    @Schema(
            description = "Identifiant de l'entrée séance.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID entryId,
    @Schema(
            description = "Début de la séance (occurred_from), UTC ISO 8601.",
            example = "2026-03-07T09:00:00Z")
        Instant occurredFrom,
    @Schema(
            description =
                "Nature : intake = accueil, session = séance, contact = hors rendez-vous.",
            allowableValues = {"intake", "session", "contact"},
            example = "session")
        String kind,
    @Schema(
            description = "Modalité : en cabinet, en visio, par téléphone ou par e-mail.",
            allowableValues = {"in_person", "video", "phone", "email"},
            example = "in_person")
        String modality,
    @Schema(description = "Durée en minutes (1 à 600) ; null si non renseignée.", example = "60")
        Integer durationMin,
    @Schema(
            description = "Date-heure de clôture, UTC ISO 8601 ; null si la séance est ouverte.",
            example = "2026-03-07T10:00:00Z")
        Instant closedAt,
    @Schema(
            description = "Titre de la séance ; null si elle n'en a pas.",
            example = "Séance de suivi")
        String title,
    @Schema(description = "Nombre d'entrées non archivées rattachées à la séance.", example = "4")
        int entryCount,
    @Schema(
            description =
                "Première ligne non vide du compte rendu (200 caractères au plus, « … » si"
                    + " tronquée) ; null sans compte rendu.",
            example = "Anxiété en baisse depuis la reprise du traitement.")
        String summaryFirstLine,
    @Schema(
            description =
                "Compte rendu entier, pour la séance la plus récente seulement ; null pour les"
                    + " autres ou sans compte rendu.")
        String summary) {}
