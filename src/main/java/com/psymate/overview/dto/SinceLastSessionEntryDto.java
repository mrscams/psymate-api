package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Entrée saisie depuis la dernière séance clôturée.")
public record SinceLastSessionEntryDto(
    @Schema(
            description = "Identifiant de l'entrée.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID entryId,
    @Schema(
            description = "Type d'entrée.",
            allowableValues = {"note", "event", "situation", "feeling", "medication"},
            example = "note")
        String type,
    @Schema(
            description = "Titre de la version courante ; null si l'entrée n'a pas de titre.",
            example = "Rêve récurrent")
        String title,
    @Schema(description = "Début (occurred_from), UTC ISO 8601.", example = "2026-03-10T18:00:00Z")
        Instant occurredFrom,
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
            description = "Identifiant de la séance de rattachement.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID sessionId) {}
