package com.psymate.identity.preferences;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Documentation OpenAPI uniquement (jamais sérialisée) : les 9 clés V1 des préférences et leurs
 * domaines, fiche 14. Les clés réelles sont plates et pointées (ADR-11).
 */
@Schema(
    name = "Preferences",
    description = "Préférences à plat du compte ; en PATCH, toutes les clés sont optionnelles.")
record PreferencesSchema(
    @Schema(
            name = "overview.view",
            allowableValues = {"simple", "complete"},
            example = "simple")
        String overviewView,
    @Schema(
            name = "overview.window",
            allowableValues = {"30d", "90d", "12m"},
            example = "90d")
        String overviewWindow,
    @Schema(
            name = "timeline.groupBy",
            allowableValues = {"session", "month", "chronological"},
            example = "session")
        String timelineGroupBy,
    @Schema(
            name = "theme",
            allowableValues = {"light", "soft", "dark", "system"},
            example = "light")
        String theme,
    @Schema(
            name = "locale",
            allowableValues = {"fr", "en"},
            example = "fr")
        String locale,
    @Schema(
            name = "vocabulary.subjectLabel",
            allowableValues = {"patient", "client"},
            description = "Défaut : client pour un coach, patient sinon",
            example = "patient")
        String vocabularySubjectLabel,
    @Schema(
            name = "entryTypes.medicationVisible",
            description = "Défaut : true pour un psychiatre, false sinon")
        Boolean entryTypesMedicationVisible,
    @Schema(
            name = "entries.defaultVisibility",
            allowableValues = {"clinical", "private"},
            example = "clinical")
        String entriesDefaultVisibility,
    @Schema(name = "sessions.defaultDurationMin", minimum = "15", maximum = "180", example = "60")
        Integer sessionsDefaultDurationMin) {}
