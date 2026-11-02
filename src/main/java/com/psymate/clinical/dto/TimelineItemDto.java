package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Élément de timeline. {@code authorType} est en lecture seule, toujours « practitioner » en V1
 * (A11). {@code ongoing} : situation sans fin.
 */
@Schema(
    description =
        "Entrée allégée de la chronologie : de quoi dessiner une ligne sans charger l'entrée"
            + " complète (GET /api/entries/{id}).")
public record TimelineItemDto(
    @Schema(description = "Identifiant de l'entrée.") UUID id,
    @Schema(
            description = "Type de l'entrée.",
            allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
            example = "feeling")
        String type,
    @Schema(description = "Moment auquel l'entrée a eu lieu, avec sa précision.")
        EntryOccurredDto occurred,
    @Schema(description = "Titre de la version courante. Null si la version n'en a pas.")
        String title,
    @Schema(description = "Émotion du ressenti. Renseignée pour le type feeling, null sinon.")
        TimelineEmotionDto emotion,
    @Schema(
            description = "Intensité du ressenti, de 0 à 10. Null pour les autres types.",
            minimum = "0",
            maximum = "10")
        Integer intensity,
    @Schema(
            description =
                "Libellés des tags actifs de l'entrée, triés sans tenir compte de la casse.")
        List<String> tags,
    @Schema(description = "Nombre de liens acceptés portant sur l'entrée.", minimum = "0")
        int linkCount,
    @Schema(
            description =
                "Identifiant de la séance de l'entrée. Null pour une séance (une séance n'est"
                    + " rattachée à aucune séance).")
        UUID sessionId,
    @Schema(
            description = "Statut : draft = brouillon ; final = définitive.",
            allowableValues = {"draft", "final"},
            example = "final")
        String status,
    @Schema(
            description =
                "Visibilité : private = visible du seul praticien auteur ; clinical = partagée"
                    + " avec l'équipe clinique.",
            allowableValues = {"private", "clinical"},
            example = "clinical")
        String visibility,
    @Schema(
            description =
                "Provenance de l'entrée. Lecture seule : vaut toujours practitioner en V1.",
            accessMode = Schema.AccessMode.READ_ONLY,
            allowableValues = {"practitioner"},
            example = "practitioner")
        String authorType,
    @Schema(
            description = "Instant d'enregistrement de l'entrée, UTC ISO 8601.",
            example = "2026-10-20T19:05:00Z")
        Instant recordedAt,
    @Schema(
            description =
                "Vrai pour une situation sans fin (occurred.to nul), toujours en cours. Faux"
                    + " pour tout autre élément.")
        boolean ongoing) {}
