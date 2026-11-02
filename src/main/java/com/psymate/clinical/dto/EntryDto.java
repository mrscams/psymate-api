package com.psymate.clinical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entrée complète. {@code authorType} est en lecture seule et vaut toujours « practitioner » en V1
 * (A11). {@code links} n'est renseigné que dans la réponse de création (POST, liste vide si aucun
 * lien demandé) ; il vaut null sur GET, PATCH et restauration, où les liens se lisent par GET
 * /api/entries/{id}/links (M2-01). {@code linkCount} est le nombre de liens acceptés.
 */
@Schema(
    description =
        "Entrée clinique complète (version courante, détail selon le type, tags). Une entrée"
            + " archivée reste lisible : archivedAt est alors renseigné. Les liens se lisent par"
            + " GET /api/entries/{id}/links ; seule la réponse de création les porte dans links.")
public record EntryDto(
    @Schema(description = "Identifiant de l'entrée.") UUID id,
    @Schema(description = "Identifiant du patient du dossier.") UUID patientId,
    @Schema(
            description = "Type de l'entrée.",
            allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
            example = "note")
        String type,
    @Schema(
            description =
                "Statut : draft = brouillon ; final = définitive. L'archivage n'est pas un"
                    + " statut, il est porté par archivedAt.",
            allowableValues = {"draft", "final"},
            example = "final")
        String status,
    @Schema(
            description =
                "Visibilité : private = visible du seul praticien auteur, exclue de l'export ;"
                    + " clinical = partagée avec l'équipe clinique.",
            allowableValues = {"private", "clinical"},
            example = "clinical")
        String visibility,
    @Schema(
            description =
                "Provenance de l'entrée. Lecture seule : vaut toujours practitioner en V1, jamais"
                    + " acceptée en écriture (422 UNKNOWN_FIELD).",
            accessMode = Schema.AccessMode.READ_ONLY,
            allowableValues = {"practitioner"},
            example = "practitioner")
        String authorType,
    @Schema(description = "Moment auquel l'entrée a eu lieu, avec sa précision.")
        EntryOccurredDto occurred,
    @Schema(
            description = "Instant d'enregistrement de l'entrée, UTC ISO 8601.",
            example = "2026-10-20T19:05:00Z")
        Instant recordedAt,
    @Schema(
            description =
                "Identifiant de la séance à laquelle l'entrée appartient. Null pour une séance"
                    + " (une séance n'est rattachée à aucune séance).")
        UUID sessionId,
    @Schema(
            description =
                "Numéro de la version courante, à partir de 1. Incrémenté à chaque correction"
                    + " hors fenêtre de grâce de 1 h 15.",
            minimum = "1")
        int version,
    @Schema(description = "Titre de la version courante. Null si la version n'en a pas.")
        String title,
    @Schema(description = "Texte de la version courante. Null si la version n'en a pas.")
        String body,
    @Schema(
            description =
                "Libellés des tags actifs de l'entrée, triés sans tenir compte de la casse."
                    + " Liste vide s'il n'y en a pas.")
        List<String> tags,
    @Schema(description = "Détail du ressenti. Renseigné pour le type feeling, null sinon.")
        EntryFeelingDto feeling,
    @Schema(description = "Détail du médicament. Renseigné pour le type medication, null sinon.")
        EntryMedicationDto medication,
    @Schema(description = "Détail de la séance. Renseigné pour le type session, null sinon.")
        EntrySessionDto session,
    @Schema(description = "Nombre de liens acceptés portant sur l'entrée.", minimum = "0")
        int linkCount,
    @Schema(
            description =
                "Liens posés à la création, dans leur sens réel. Renseigné uniquement dans la"
                    + " réponse 201 de POST /api/patients/{id}/entries (liste vide si aucun lien"
                    + " demandé) ; null partout ailleurs (utiliser GET /api/entries/{id}/links).")
        List<EntryLinkDto> links,
    @Schema(
            description = "Instant d'archivage UTC. Null si l'entrée n'est pas archivée.",
            example = "2026-10-21T08:00:00Z")
        Instant archivedAt) {}
