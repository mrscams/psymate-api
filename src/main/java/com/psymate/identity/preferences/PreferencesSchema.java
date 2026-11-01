package com.psymate.identity.preferences;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Documentation OpenAPI uniquement (jamais sérialisée) : les 9 clés V1 des préférences et leurs
 * domaines, fiche 14. Les clés réelles sont plates et pointées (ADR-11).
 */
@Schema(
    name = "Preferences",
    description =
        "Préférences à plat du compte : 9 clés connues, aux noms pointés (par exemple"
            + " \"overview.view\"), sans objet imbriqué. En lecture, les 9 clés sont toujours"
            + " présentes (valeur enregistrée, sinon défaut selon la profession). En PATCH, toutes"
            + " les clés sont optionnelles : null remet la clé à son défaut, une clé inconnue est"
            + " ignorée.")
record PreferencesSchema(
    @Schema(
            name = "overview.view",
            description =
                "Vue d'ensemble du dossier : simple = version condensée, complete = version"
                    + " détaillée. Défaut : simple.",
            allowableValues = {"simple", "complete"},
            example = "simple")
        String overviewView,
    @Schema(
            name = "overview.window",
            description =
                "Fenêtre de temps de la vue d'ensemble : 30d = 30 jours, 90d = 90 jours, 12m = 12"
                    + " mois. Défaut : 90d.",
            allowableValues = {"30d", "90d", "12m"},
            example = "90d")
        String overviewWindow,
    @Schema(
            name = "timeline.groupBy",
            description =
                "Regroupement de la chronologie : session = par séance, month = par mois,"
                    + " chronological = liste chronologique sans regroupement. Défaut : session.",
            allowableValues = {"session", "month", "chronological"},
            example = "session")
        String timelineGroupBy,
    @Schema(
            name = "theme",
            description =
                "Thème visuel de l'application : light = clair, soft = doux, dark = sombre, system"
                    + " = celui du système. Défaut : light.",
            allowableValues = {"light", "soft", "dark", "system"},
            example = "light")
        String theme,
    @Schema(
            name = "locale",
            description = "Langue de l'interface : fr (français) ou en (anglais). Défaut : fr.",
            allowableValues = {"fr", "en"},
            example = "fr")
        String locale,
    @Schema(
            name = "vocabulary.subjectLabel",
            description =
                "Mot employé pour désigner la personne suivie : patient ou client. Défaut :"
                    + " client pour un coach, patient sinon.",
            allowableValues = {"patient", "client"},
            example = "patient")
        String vocabularySubjectLabel,
    @Schema(
            name = "entryTypes.medicationVisible",
            description =
                "true si le type d'entrée « médication » est proposé à la saisie. Défaut : true"
                    + " pour un psychiatre, false sinon.")
        Boolean entryTypesMedicationVisible,
    @Schema(
            name = "entries.defaultVisibility",
            description =
                "Visibilité proposée par défaut pour une nouvelle entrée : clinical = clinique,"
                    + " private = privée. Défaut : clinical.",
            allowableValues = {"clinical", "private"},
            example = "clinical")
        String entriesDefaultVisibility,
    @Schema(
            name = "sessions.defaultDurationMin",
            description =
                "Durée proposée par défaut pour une séance, en minutes (entier de 15 à 180)."
                    + " Défaut : 60.",
            minimum = "15",
            maximum = "180",
            example = "60")
        Integer sessionsDefaultDurationMin) {}
