package com.psymate.patients.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(
    description =
        "Information du Profil 360 d'un patient : ce qui décrit la personne, hors séance. Elle"
            + " n'appartient à aucune séance et se corrige sur place, sans historique.")
public record PatientProfileItemDto(
    @Schema(
            description = "Identifiant de l'information.",
            example = "3f1c2a9e-8b7d-4c6e-a5f4-1e2d3c4b5a69")
        UUID id,
    @Schema(
            description =
                "Rubrique : history = Histoire et souvenirs, family = Famille et entourage,"
                    + " health = Santé et antécédents, values_goals = Valeurs et projets.",
            allowableValues = {"history", "family", "health", "values_goals"},
            example = "history")
        String category,
    @Schema(
            description = "L'information elle-même, 200 caractères au plus.",
            example = "A grandi à Nantes, aînée de trois enfants")
        String title,
    @Schema(
            description =
                "Seconde ligne facultative (précision, citation), 2 000 caractères au plus ; null"
                    + " si aucune.",
            example = "Dit en garder « un souvenir de maison pleine »")
        String detail,
    @Schema(
            description =
                "Période libre, affichée telle quelle et jamais interprétée (history seulement) ;"
                    + " null si aucune ou hors history.",
            example = "Enfance")
        String periodLabel,
    @Schema(
            description =
                "Année de tri du fil chronologique, 1900 à 2100 (history seulement) ; null si"
                    + " aucune ou hors history.",
            example = "1998")
        Integer periodYear,
    @Schema(description = "Date-heure de création, UTC ISO 8601.", example = "2026-11-18T09:30:00Z")
        OffsetDateTime createdAt,
    @Schema(
            description =
                "Date-heure de la dernière correction (égale à createdAt si jamais corrigée), UTC"
                    + " ISO 8601.",
            example = "2026-11-18T09:45:00Z")
        OffsetDateTime updatedAt) {}
