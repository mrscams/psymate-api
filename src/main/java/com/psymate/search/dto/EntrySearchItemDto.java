package com.psymate.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(
    description =
        "Une entrée trouvée. title et snippet sont des fragments HTML échappés ; seule la"
            + " balise mark y est ajoutée, autour des mots trouvés.")
public record EntrySearchItemDto(
    @Schema(
            description = "Identifiant de l'entrée.",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID entryId,
    @Schema(
            description = "Code du type d'entrée.",
            allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
            example = "note")
        String type,
    @Schema(
            description =
                "Titre de la version courante en HTML échappé, mots trouvés entourés de mark si"
                    + " la recherche a un texte ; null si l'entrée n'a pas de titre.",
            example = "Le <mark>licenciement</mark> de son frère")
        String title,
    @Schema(
            description =
                "Extrait du corps en HTML échappé, mots trouvés entourés de mark (sans texte de"
                    + " recherche : début du corps, 200 caractères au plus, « … » si tronqué) ;"
                    + " null si l'entrée n'a pas de corps.",
            example = "…n'a pas supporté le <mark>licenciement</mark> de son frère…")
        String snippet,
    @Schema(
            description = "Début de l'événement concerné (occurred_from), UTC ISO 8601.",
            example = "2026-03-10T10:00:00Z")
        Instant occurredFrom,
    @Schema(
            description =
                "Fin de l'événement concerné (occurred_to), UTC ISO 8601 ; null si ponctuel ou"
                    + " en cours.",
            example = "2026-03-10T11:00:00Z")
        Instant occurredTo,
    @Schema(
            description =
                "Précision de la date : exact = date-heure connue, day = jour connu, month ="
                    + " mois connu, year = année connue, period = période, life_phase = phase de"
                    + " vie.",
            allowableValues = {"exact", "day", "month", "year", "period", "life_phase"},
            example = "exact")
        String occurredPrecision,
    @Schema(
            description =
                "Identifiant de la séance de rattachement ; null pour une entrée de type session"
                    + " (une séance n'est rattachée à aucune séance).",
            example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
        UUID sessionId,
    @Schema(
            description =
                "Visibilité : private = réservée à son auteur, clinical = dossier clinique.",
            allowableValues = {"private", "clinical"},
            example = "clinical")
        String visibility,
    @Schema(
            description =
                "Date-heure d'archivage, UTC ISO 8601 ; null si l'entrée n'est pas archivée"
                    + " (toujours null sans includeArchived).",
            example = "2026-04-02T08:00:00Z")
        Instant archivedAt,
    @Schema(description = "Numéro de la version courante de l'entrée.", example = "1") int version,
    @Schema(description = "Libellés des tags de l'entrée, triés ; liste vide si aucun.")
        List<String> tags,
    @Schema(
            description = "Code de l'émotion pour un ressenti, null pour les autres types.",
            example = "anxiety")
        String emotionCode,
    @Schema(
            description =
                "Pertinence (ts_rank_cd, multipliée par 1,5 pour les entrées de moins de 90"
                    + " jours) ; null pour un résultat approché ou une recherche sans texte.",
            example = "0.31")
        Double rank,
    @Schema(
            description =
                "Similarité trigramme de 0 à 1 (seuil 0,3) ; null pour un résultat exact.",
            minimum = "0",
            maximum = "1",
            example = "0.45")
        Double similarity,
    @Schema(
            description =
                "true pour un résultat approché (tolérance aux fautes de frappe), false pour un"
                    + " résultat exact.",
            example = "false")
        boolean approximate) {}
