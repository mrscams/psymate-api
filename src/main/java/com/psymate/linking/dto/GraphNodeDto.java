package com.psymate.linking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Nœud : une entrée du patient, avec son degré dans tout le dossier. */
@Schema(description = "Nœud du graphe : une entrée non archivée du patient.")
public record GraphNodeDto(
    @Schema(
            description = "Identifiant de l'entrée.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID id,
    @Schema(
            description =
                "Code du type d'entrée : note, event, situation, feeling, session ou"
                    + " medication (référentiel GET /api/vocabulary/entry-types).",
            example = "feeling")
        String type,
    @Schema(
            description = "Titre de l'entrée ; null si elle n'en a pas.",
            example = "Peur au réveil")
        String title,
    @Schema(
            description =
                "Début de l'occurrence de l'entrée (occurred.from), ISO 8601 UTC ; null si"
                    + " l'entrée n'a pas de date.",
            example = "2026-10-12T07:30:00Z")
        Instant occurred,
    @Schema(
            description = "Intensité de 0 à 10 ; null sauf pour un ressenti (feeling).",
            minimum = "0",
            maximum = "10",
            example = "7")
        Integer intensity,
    @Schema(
            description =
                "Code de l'émotion (référentiel GET /api/vocabulary/emotions) ; null sauf pour"
                    + " un ressenti (feeling).",
            example = "fear")
        String emotion,
    @Schema(
            description =
                "Nombre de liens acceptés de l'entrée dans tout le dossier du patient, pas"
                    + " seulement dans le graphe rendu.",
            example = "3")
        int degree,
    @Schema(
            description =
                "Distance à la racine en nombre de liens : 0 pour la racine, 1 pour tous les"
                    + " nœuds d'un graphe sans racine et pour les membres d'un expand.",
            example = "1")
        int distance,
    @Schema(
            description =
                "Auteur de l'entrée : toujours practitioner en V1. Lecture seule, jamais accepté"
                    + " en écriture.",
            accessMode = Schema.AccessMode.READ_ONLY,
            allowableValues = {"practitioner"},
            example = "practitioner")
        String authorType) {}
