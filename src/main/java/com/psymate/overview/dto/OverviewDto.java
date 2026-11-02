package com.psymate.overview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Corps de la réponse de la vue d'ensemble. Les blocs {@code facts}, {@code activeSituations},
 * {@code trend}, {@code emotions} et {@code drafts} sont ceux de la projection (ils sont stockés
 * tels quels dans {@code patient_overview.payload}) ; {@code recentActivity} est lu en direct.
 */
@Schema(
    description =
        "Synthèse clinique d'un patient pour une fenêtre (30d, 90d ou 12m) : faits clés,"
            + " situations actives, tendance, émotions dominantes, ressentis, brouillons,"
            + " activité récente, résumé rédigé et séances récentes.")
public record OverviewDto(
    @Schema(
            description = "Fenêtre appliquée à trend, emotions et feelings.",
            allowableValues = {"30d", "90d", "12m"},
            example = "90d")
        String window,
    @Schema(description = "Faits clés du dossier, indépendants de la fenêtre.")
        OverviewFactsDto facts,
    @Schema(
            description =
                "Situations en cours (sans fin connue), triées par nombre de liens décroissant ;"
                    + " liste vide s'il n'y en a pas.")
        List<ActiveSituationDto> activeSituations,
    @Schema(description = "Courbe d'intensité et repères de la fenêtre.") TrendDto trend,
    @Schema(description = "Émotions dominantes de la fenêtre.") EmotionsDto emotions,
    @Schema(description = "Brouillons en attente et séance ouverte.") DraftsDto drafts,
    @Schema(
            description =
                "10 dernières entrées non archivées saisies ou corrigées, de la plus récente à la"
                    + " plus ancienne, lues en direct (hors projection) ; entrées privées"
                    + " incluses.")
        List<RecentActivityItemDto> recentActivity,
    @Schema(
            description =
                "Résumé « En bref » rédigé par règles (sans IA), dans la langue d'Accept-Language.")
        NarrativeDto narrative,
    @Schema(
            description =
                "Ressentis réels de la fenêtre : un point par ressenti, une série par émotion.")
        FeelingsDto feelings,
    @Schema(description = "Entrées saisies depuis la dernière séance clôturée.")
        SinceLastSessionDto sinceLastSession,
    @Schema(
            description =
                "5 dernières séances non archivées, de la plus récente à la plus ancienne ; la"
                    + " plus récente porte son compte rendu entier.")
        List<RecentSessionDto> recentSessions,
    @Schema(
            description =
                "Date-heure du calcul de la projection, UTC ISO 8601 (jamais d'un jour UTC"
                    + " antérieur à aujourd'hui).",
            example = "2026-03-14T09:30:00Z")
        Instant computedAt,
    @Schema(
            description =
                "Révision du dossier (patient.clinical_rev) au moment du calcul : compteur"
                    + " croissant, utile pour détecter un changement.",
            example = "42")
        long sourceRev) {}
