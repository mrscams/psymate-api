package com.psymate.linking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Lien tel que le client l'affiche. {@code label} est le verbe vu depuis l'entrée de référence (le
 * verbe inverse quand elle est la cible d'un lien dirigé) et {@code sentence} la phrase lisible «
 * entrée verbe autre entrée », dans la langue de la requête.
 */
@Schema(
    description =
        "Lien entre deux entrées d'un même patient, vu depuis une entrée de référence (l'entrée"
            + " de l'URL ; la source du lien pour un changement de type).")
public record LinkDto(
    @Schema(description = "Identifiant du lien.", example = "5d2f7a10-8c4b-4e6a-b1d3-7f9e0a2c4b68")
        UUID id,
    @Schema(
            description = "Identifiant de l'entrée source du lien.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID sourceId,
    @Schema(
            description = "Identifiant de l'entrée cible du lien.",
            example = "c1a7e9d4-2b3f-4a6c-8d5e-0f1a2b3c4d5e")
        UUID targetId,
    @Schema(
            description =
                "Code du type de lien (triggers, caused_by, worsens, relieves, co_occurs,"
                    + " follows, part_of, treats). Toujours le code du sens source vers cible,"
                    + " jamais un type passif.",
            example = "triggers")
        String linkType,
    @Schema(
            description =
                "Verbe du lien vu depuis l'entrée de référence, dans la langue de la requête :"
                    + " le libellé direct si elle est la source, le libellé inverse si elle est"
                    + " la cible d'un type dirigé (par exemple « déclenche » ou « déclenché"
                    + " par »).",
            example = "déclenche")
        String label,
    @Schema(
            description =
                "Phrase lisible « entrée verbe autre entrée » vue depuis l'entrée de référence,"
                    + " dans la langue de la requête ; chaque entrée y est désignée par son"
                    + " titre, sinon par un extrait de 60 caractères de son texte, sinon par"
                    + " « une entrée ».",
            example = "Décès du père déclenche Peur au réveil")
        String sentence,
    @Schema(
            description =
                "Origine du lien : manual (posé par le praticien), seule valeur produite en V1."
                    + " Valeurs réservées à l'IA (V2) : ai_suggested, ai_confirmed.",
            example = "manual")
        String provenance,
    @Schema(
            description =
                "Statut du lien : accepted (lien actif), seule valeur rendue en V1. Valeurs"
                    + " réservées : proposed et rejected (suggestions de l'IA, V2), superseded"
                    + " (lien remplacé ou supprimé, jamais rendu par cette API).",
            example = "accepted")
        String status,
    @Schema(
            description = "Justification saisie à la pose du lien ; null si aucune.",
            example = "Depuis le décès, les réveils sont angoissés.")
        String rationale,
    @Schema(
            description = "Date de création du lien, ISO 8601 UTC.",
            example = "2026-10-12T14:30:00Z")
        Instant createdAt,
    @Schema(
            description =
                "Identifiant du praticien qui a posé le lien ; null pour une suggestion de l'IA"
                    + " (V2, jamais en V1).",
            example = "7e0b3d92-4c1a-4f58-a6b7-9c8d7e6f5a4b")
        UUID authorId) {}
