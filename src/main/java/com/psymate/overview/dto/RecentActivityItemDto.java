package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Entrée récente du dossier.")
public record RecentActivityItemDto(
    @Schema(
            description = "Identifiant de l'entrée.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID entryId,
    @Schema(
            description = "Type d'entrée.",
            allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
            example = "note")
        String type,
    @Schema(
            description = "Titre de la version courante ; null si l'entrée n'a pas de titre.",
            example = "Rêve récurrent")
        String title,
    @Schema(
            description = "Début de l'événement concerné (occurred_from), UTC ISO 8601.",
            example = "2026-03-13T18:00:00Z")
        Instant occurredFrom,
    @Schema(
            description =
                "Fin de l'événement concerné (occurred_to), UTC ISO 8601 ; null si ponctuel.",
            example = "2026-03-13T19:00:00Z")
        Instant occurredTo,
    @Schema(
            description = "État de l'entrée : draft = brouillon, final = validée.",
            allowableValues = {"draft", "final"},
            example = "final")
        String status,
    @Schema(
            description =
                "Visibilité : private = réservée au praticien, clinical = dossier clinique.",
            allowableValues = {"private", "clinical"},
            example = "clinical")
        String visibility,
    @Schema(
            description =
                "Identifiant de la séance de rattachement ; null pour une entrée de type session"
                    + " (une séance n'est rattachée à aucune séance).",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID sessionId,
    @Schema(description = "Numéro de la version courante de l'entrée.", example = "1") int version,
    @Schema(
            description = "Date-heure de saisie de l'entrée, UTC ISO 8601.",
            example = "2026-03-14T09:30:00Z")
        Instant recordedAt) {}
