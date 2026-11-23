package com.psymate.identity.preferences;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Documentation OpenAPI uniquement (jamais sérialisée) : les 21 clés des préférences et leurs
 * domaines, fiche 14, agenda (G Q57) et prise de rendez-vous en ligne (Q75). Les clés réelles sont
 * plates et pointées (ADR-11).
 */
@Schema(
    name = "Preferences",
    description =
        "Préférences à plat du compte : 21 clés connues, aux noms pointés (par exemple"
            + " \"overview.view\"), jamais imbriquées les unes dans les autres (seule la valeur de"
            + " booking.windows est une liste d'objets). En lecture, les 21 clés sont toujours"
            + " présentes (valeur enregistrée, sinon défaut selon la profession). En PATCH, toutes"
            + " les clés sont optionnelles : null remet la clé à son défaut, une clé inconnue est"
            + " ignorée. Les bornes de la journée de l'agenda doivent rester cohérentes :"
            + " agenda.dayEndHour strictement supérieur à agenda.dayStartHour, sinon 422"
            + " OUT_OF_RANGE sur agenda.dayEndHour. Aucune règle croisée entre les clés"
            + " booking.*.")
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
            description =
                "Langue de l'interface : fr (français), en (anglais) ou ar (arabe). Défaut : fr.",
            allowableValues = {"fr", "en", "ar"},
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
        String agendaDefaultView,
    @Schema(
            name = "booking.enabled",
            description =
                "true si le praticien propose la prise de rendez-vous en ligne sur sa fiche"
                    + " publique. Elle n'est réellement offerte que s'il existe au moins une plage"
                    + " (booking.windows) et si le profil est publié et le praticien approuvé ;"
                    + " true sans plage est accepté. Autre type qu'un booléen : 422 INVALID_VALUE."
                    + " Défaut : false.",
            example = "false")
        Boolean bookingEnabled,
    @Schema(
            name = "booking.confirmation",
            description =
                "Confirmation des demandes de rendez-vous en ligne : manual = le praticien accepte"
                    + " chaque demande, auto = rendez-vous confirmé dès que le demandeur a vérifié"
                    + " son e-mail. Autre valeur :"
                    + " 422 INVALID_VALUE. Défaut : manual.",
            allowableValues = {"manual", "auto"},
            example = "manual")
        String bookingConfirmation,
    @Schema(
            name = "booking.windows",
            example = "[{\"day\":1,\"start\":\"09:00\",\"end\":\"12:00\"}]")
        @ArraySchema(
            maxItems = 21,
            arraySchema =
                @Schema(
                    name = "booking.windows",
                    description =
                        "Plages hebdomadaires ouvertes à la prise de rendez-vous en ligne, 21 au"
                            + " plus, renvoyées triées par jour puis par début. Deux plages du"
                            + " même jour ne se chevauchent pas (09:00–12:00 puis 12:00–14:00 sont"
                            + " contiguës, donc admises). Plus de 21 plages : 422 OUT_OF_RANGE ;"
                            + " plage mal formée, champ manquant ou inconnu, chevauchement ou"
                            + " autre type : 422 INVALID_VALUE. Liste vide admise. Défaut : []."))
        List<BookingWindowSchema> bookingWindows,
    @Schema(
            name = "booking.slotDurationMin",
            description =
                "Durée d'un créneau proposé en ligne, en minutes : entier de 15 à 180, multiple de"
                    + " 5. Hors bornes ou non multiple de 5 : 422 OUT_OF_RANGE ; non entier : 422"
                    + " INVALID_VALUE. Défaut : 60.",
            minimum = "15",
            maximum = "180",
            multipleOf = 5,
            example = "60")
        Integer bookingSlotDurationMin,
    @Schema(
            name = "booking.minNoticeHours",
            description =
                "Délai minimal, en heures, entre la demande et le début du créneau : entier de 1 à"
                    + " 168. Hors bornes : 422 OUT_OF_RANGE ; non entier : 422 INVALID_VALUE."
                    + " Défaut : 24.",
            minimum = "1",
            maximum = "168",
            example = "24")
        Integer bookingMinNoticeHours,
    @Schema(
            name = "booking.horizonDays",
            description =
                "Horizon, en jours, au-delà duquel aucun créneau n'est proposé : entier de 7 à 90."
                    + " Hors bornes : 422 OUT_OF_RANGE ; non entier : 422 INVALID_VALUE. Défaut :"
                    + " 28.",
            minimum = "7",
            maximum = "90",
            example = "28")
        Integer bookingHorizonDays,
    @Schema(name = "booking.modalities", example = "[\"in_person\"]")
        @ArraySchema(
            minItems = 1,
            maxItems = 3,
            uniqueItems = true,
            schema = @Schema(allowableValues = {"in_person", "video", "phone"}),
            arraySchema =
                @Schema(
                    name = "booking.modalities",
                    description =
                        "Modalités proposées en ligne : liste non vide, sans doublon, parmi"
                            + " in_person (au cabinet), video (visio) et phone (téléphone), dans"
                            + " l'ordre enregistré. Liste vide, doublon, autre valeur ou autre"
                            + " type : 422 INVALID_VALUE. Défaut : [\"in_person\"]."))
        List<String> bookingModalities,
    @Schema(
            name = "booking.timezone",
            description =
                "Fuseau horaire des plages : identifiant de la base IANA (tz) connu de java.time"
                    + " (par exemple Europe/Paris, America/Montreal). Décalage (+02:00, UTC+1), nom"
                    + " incomplet"
                    + " (Paris) ou autre type : 422 INVALID_VALUE. Défaut : Europe/Paris.",
            example = "Europe/Paris")
        String bookingTimezone) {

  /** Une plage hebdomadaire de booking.windows (Q75). */
  @Schema(
      name = "BookingWindow",
      description =
          "Plage hebdomadaire de prise de rendez-vous en ligne : exactement les trois champs day,"
              + " start et end (champ manquant ou inconnu : 422 INVALID_VALUE sur"
              + " booking.windows). end est strictement après start.")
  record BookingWindowSchema(
      @Schema(
              description = "Jour de la semaine : entier de 1 (lundi) à 7 (dimanche).",
              minimum = "1",
              maximum = "7",
              example = "1")
          Integer day,
      @Schema(
              description =
                  "Début de la plage, heure locale du fuseau booking.timezone, au format HH:mm"
                      + " (00:00 à 23:55), minutes multiples de 5.",
              pattern = "^([01][0-9]|2[0-3]):[0-5][05]$",
              example = "09:00")
          String start,
      @Schema(
              description =
                  "Fin de la plage, exclue, au format HH:mm (00:00 à 23:55), minutes multiples de"
                      + " 5, strictement après start.",
              pattern = "^([01][0-9]|2[0-3]):[0-5][05]$",
              example = "12:00")
          String end) {}
}
