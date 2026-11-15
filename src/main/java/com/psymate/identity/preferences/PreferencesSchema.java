package com.psymate.identity.preferences;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Documentation OpenAPI uniquement (jamais sérialisée) : les 13 clés V1 des préférences et leurs
 * domaines, fiche 14 et agenda (G Q57). Les clés réelles sont plates et pointées (ADR-11).
 */
@Schema(
    name = "Preferences",
    description =
        "Préférences à plat du compte : 13 clés connues, aux noms pointés (par exemple"
            + " \"overview.view\"), sans objet imbriqué. En lecture, les 13 clés sont toujours"
            + " présentes (valeur enregistrée, sinon défaut selon la profession). En PATCH, toutes"
            + " les clés sont optionnelles : null remet la clé à son défaut, une clé inconnue est"
            + " ignorée. Les bornes de la journée de l'agenda doivent rester cohérentes :"
            + " agenda.dayEndHour strictement supérieur à agenda.dayStartHour, sinon 422"
            + " OUT_OF_RANGE sur agenda.dayEndHour.")
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
        Integer sessionsDefaultDurationMin,
    @Schema(
            name = "agenda.dayStartHour",
            description =
                "Première heure affichée dans la grille de l'agenda (entier de 0 à 23). Hors"
                    + " bornes : 422 OUT_OF_RANGE. Défaut : 8.",
            minimum = "0",
            maximum = "23",
            example = "8")
        Integer agendaDayStartHour,
    @Schema(
            name = "agenda.dayEndHour",
            description =
                "Dernière heure affichée dans la grille de l'agenda (entier de 1 à 24),"
                    + " strictement supérieure à agenda.dayStartHour après fusion. Hors bornes ou"
                    + " non supérieure au début : 422 OUT_OF_RANGE. Défaut : 19.",
            minimum = "1",
            maximum = "24",
            example = "19")
        Integer agendaDayEndHour,
    @Schema(
            name = "agenda.workingDays",
            description =
                "Jours travaillés affichés dans l'agenda : tableau d'entiers de 1 (lundi) à 7"
                    + " (dimanche), sans doublon, au moins un jour, dans l'ordre enregistré"
                    + " (jour hors 1–7 : 422 OUT_OF_RANGE ; doublon, liste vide ou autre type :"
                    + " 422 INVALID_VALUE). Défaut : [1,2,3,4,5,6].",
            example = "[1,2,3,4,5,6]")
        List<Integer> agendaWorkingDays,
    @Schema(
            name = "agenda.defaultView",
            description =
                "Vue ouverte par défaut dans l'agenda : day = jour, week = semaine, month = mois,"
                    + " list = liste. Défaut : week.",
            allowableValues = {"day", "week", "month", "list"},
            example = "week")
        String agendaDefaultView) {}
