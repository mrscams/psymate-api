package com.psymate.overview;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Corps de la réponse de la vue d'ensemble. Les blocs {@code facts}, {@code activeSituations},
 * {@code trend}, {@code emotions} et {@code drafts} sont ceux de la projection (ils sont stockés
 * tels quels dans {@code patient_overview.payload}) ; {@code recentActivity} est lu en direct.
 */
final class OverviewDtos {

  private OverviewDtos() {}

  @Schema(
      description =
          "Synthèse clinique d'un patient pour une fenêtre (30d, 90d ou 12m) : faits clés,"
              + " situations actives, tendance, émotions dominantes, ressentis, brouillons,"
              + " activité récente, résumé rédigé et séances récentes.")
  record OverviewDto(
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

  @Schema(description = "Faits clés du patient.")
  record OverviewFactsDto(
      @Schema(
              description =
                  "Âge en années révolues aujourd'hui ; null si la date de naissance n'est pas"
                      + " renseignée.",
              example = "34")
          Integer age,
      @Schema(
              description =
                  "Date de début du suivi AAAA-MM-JJ : follow_up.since de la fiche si elle est"
                      + " valide, sinon la date de création du dossier.",
              example = "2025-09-01")
          LocalDate followedSince,
      @Schema(description = "Nombre de séances non archivées.", example = "12") int sessionCount,
      @Schema(
              description =
                  "Médicaments en cours (fin ni connue ni passée), une ligne par molécule, du plus"
                      + " récent au plus ancien ; liste vide s'il n'y en a pas.")
          List<ActiveMedicationDto> activeMedications,
      @Schema(description = "Drapeaux actifs (non levés), du plus ancien au plus récent.")
          List<OverviewFlagDto> flags) {}

  @Schema(description = "Médicament en cours.")
  record ActiveMedicationDto(
      @Schema(
              description = "Identifiant de l'entrée médicament.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID entryId,
      @Schema(description = "Molécule.", example = "sertraline") String molecule,
      @Schema(description = "Dosage ; null si non saisi.", example = "50 mg") String dosage,
      @Schema(description = "Fréquence de prise ; null si non saisie.", example = "matin")
          String frequency,
      @Schema(description = "Prescripteur ; null si non saisi.", example = "Dr Martin")
          String prescriber,
      @Schema(
              description = "Début de l'entrée (occurred_from), UTC ISO 8601.",
              example = "2026-01-10T08:00:00Z")
          Instant since,
      @Schema(
              description = "Début de la période de prise AAAA-MM-JJ ; null si non saisi.",
              example = "2026-01-10")
          LocalDate periodFrom,
      @Schema(
              description =
                  "Fin de la période de prise AAAA-MM-JJ ; null si non saisie (traitement en"
                      + " cours).",
              example = "2026-06-30")
          LocalDate periodTo) {}

  @Schema(description = "Drapeau actif posé à la main sur le dossier.")
  record OverviewFlagDto(
      @Schema(
              description =
                  "Nature : watch = à surveiller, risk = risque, shared_care = suivi partagé.",
              allowableValues = {"watch", "risk", "shared_care"},
              example = "watch")
          String kind,
      @Schema(
              description = "Raison saisie ; null si aucune (toujours renseignée pour watch).",
              example = "Troubles du sommeil")
          String reason,
      @Schema(description = "Date-heure de pose, UTC ISO 8601.", example = "2026-03-14T09:30:00Z")
          Instant setAt) {}

  @Schema(description = "Situation en cours (occurred_to nul).")
  record ActiveSituationDto(
      @Schema(
              description = "Identifiant de l'entrée situation.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID entryId,
      @Schema(
              description = "Titre de la situation ; null si elle n'en a pas.",
              example = "Conflit au travail")
          String title,
      @Schema(
              description = "Début de la situation (occurred_from), UTC ISO 8601.",
              example = "2026-02-01T00:00:00Z")
          Instant since,
      @Schema(description = "Nombre de liens de la situation.", example = "3") int linkCount,
      @Schema(
              description =
                  "Ressentis non archivés reliés par un lien accepté, dans un sens ou l'autre.",
              example = "2")
          int linkedFeelingCount,
      @Schema(
              description =
                  "Moyenne des intensités (0 à 10, deux décimales) de ces ressentis ; null s'il"
                      + " n'y en a aucun.",
              example = "6.5")
          Double averageIntensity) {}

  @Schema(description = "Courbe d'intensité et repères de la fenêtre.")
  record TrendDto(
      @Schema(
              description =
                  "Points de la courbe, dates croissantes, la dernière étant aujourd'hui : un par"
                      + " jour pour 30d et 90d, un par semaine pour 12m ; 90 au plus.")
          List<TrendPointDto> points,
      @Schema(
              description =
                  "Séances et débuts/fins de médicament dont le jour tombe dans la fenêtre, par"
                      + " date croissante.")
          List<TrendMarkerDto> markers) {}

  @Schema(description = "Point de la courbe : moyenne glissante sur 7 jours à cette date.")
  record TrendPointDto(
      @Schema(description = "Jour UTC du point, AAAA-MM-JJ.", example = "2026-03-14")
          LocalDate date,
      @Schema(
              description =
                  "Intensité moyenne (0 à 10, deux décimales) des ressentis des 7 derniers jours"
                      + " jusqu'à cette date ; null si aucun ressenti.",
              example = "5.25")
          Double averageIntensity,
      @Schema(
              description =
                  "Valence moyenne (-2 à 2, deux décimales) des valences connues sur les 7 jours ;"
                      + " null si aucune. Toujours null tant que les valences des émotions ne sont"
                      + " pas renseignées.",
              example = "-0.5")
          Double valence) {}

  @Schema(description = "Repère sur la courbe : séance ou début/fin de médicament.")
  record TrendMarkerDto(
      @Schema(description = "Jour UTC du repère, AAAA-MM-JJ.", example = "2026-03-10")
          LocalDate date,
      @Schema(
              description = "Nature du repère : session ou medication.",
              allowableValues = {"session", "medication"},
              example = "session")
          String kind,
      @Schema(
              description = "Identifiant de l'entrée séance ou médicament.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID entryId,
      @Schema(
              description =
                  "Titre de la séance (null sans titre) ou « molécule dosage » du médicament"
                      + " (« molécule » seule sans dosage).",
              example = "sertraline 50 mg")
          String label,
      @Schema(
              description =
                  "Pour une séance : intake (accueil), session (séance) ou contact (hors"
                      + " rendez-vous) ; null pour un médicament.",
              allowableValues = {"intake", "session", "contact"},
              example = "session")
          String sessionKind,
      @Schema(
              description =
                  "Pour un médicament : start (début) ou end (fin) ; null pour une séance.",
              allowableValues = {"start", "end"},
              example = "start")
          String change) {}

  @Schema(description = "Émotions dominantes de la fenêtre.")
  record EmotionsDto(
      @Schema(
              description =
                  "5 émotions au plus, par nombre de ressentis décroissant puis par code ; liste"
                      + " vide sans ressenti dans la fenêtre.")
          List<TopEmotionDto> top) {}

  @Schema(description = "Émotion dominante et variation par rapport à la fenêtre précédente.")
  record TopEmotionDto(
      @Schema(description = "Code de l'émotion.", example = "anxiety") String code,
      @Schema(description = "Libellé français de l'émotion.", example = "Anxiété") String label,
      @Schema(description = "Nombre de ressentis dans la fenêtre.", example = "8") int count,
      @Schema(
              description = "Nombre de ressentis dans la fenêtre précédente, de même longueur.",
              example = "5")
          int previousCount,
      @Schema(description = "count moins previousCount (négatif si en baisse).", example = "3")
          int delta) {}

  @Schema(description = "Brouillons et séance ouverte.")
  record DraftsDto(
      @Schema(description = "Nombre d'entrées en brouillon non archivées.", example = "2")
          int count,
      @Schema(description = "Séance non clôturée la plus récente ; null s'il n'y en a pas.")
          OpenSessionDto openSession) {}

  @Schema(description = "Séance ouverte (non clôturée).")
  record OpenSessionDto(
      @Schema(
              description = "Identifiant de l'entrée séance.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID entryId,
      @Schema(
              description = "Début de la séance (occurred_from), UTC ISO 8601.",
              example = "2026-03-14T09:00:00Z")
          Instant startedAt) {}

  @Schema(description = "Entrée récente du dossier.")
  record RecentActivityItemDto(
      @Schema(
              description = "Identifiant de l'entrée.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID entryId,
      @Schema(
              description = "Type d'entrée.",
              allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
              example = "note")
          String type,
      @Schema(
              description = "Titre de la version courante ; null si l'entrée n'a pas de titre.",
              example = "Rêve récurrent")
          String title,
      @Schema(
              description = "Début de l'événement concerné (occurred_from), UTC ISO 8601.",
              example = "2026-03-13T18:00:00Z")
          Instant occurredFrom,
      @Schema(
              description =
                  "Fin de l'événement concerné (occurred_to), UTC ISO 8601 ; null si ponctuel.",
              example = "2026-03-13T19:00:00Z")
          Instant occurredTo,
      @Schema(
              description = "État de l'entrée : draft = brouillon, final = validée.",
              allowableValues = {"draft", "final"},
              example = "final")
          String status,
      @Schema(
              description =
                  "Visibilité : private = réservée au praticien, clinical = dossier clinique.",
              allowableValues = {"private", "clinical"},
              example = "clinical")
          String visibility,
      @Schema(
              description = "Identifiant de la séance de rattachement.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID sessionId,
      @Schema(description = "Numéro de la version courante de l'entrée.", example = "1")
          int version,
      @Schema(
              description = "Date-heure de saisie de l'entrée, UTC ISO 8601.",
              example = "2026-03-14T09:30:00Z")
          Instant recordedAt) {}

  @Schema(
      description =
          "Résumé rédigé par règles (jusqu'à 5 phrases, chacune omise si ses données manquent,"
              + " sans IA), dans la langue de la requête (Accept-Language : français par défaut,"
              + " anglais si demandé).")
  record NarrativeDto(
      @Schema(
              description =
                  "Phrases dans l'ordre : cadre du suivi, tendance, situations ouvertes, événement"
                      + " non commenté, traitement en cours ; chacune est omise si ses données"
                      + " manquent (liste éventuellement vide).")
          List<String> sentences,
      @Schema(
              description =
                  "Point d'attention issu des drapeaux actifs ; null sans drapeau. Le mot « risque »"
                      + " n'apparaît jamais.",
              example = "À surveiller depuis le 14 mars 2026 (motif : Troubles du sommeil).")
          String attention) {}

  @Schema(description = "Ressentis réels de la fenêtre, repères de médicament et jours de séance.")
  record FeelingsDto(
      @Schema(
              description =
                  "Exactement un point par ressenti non archivé de la fenêtre, du plus ancien au"
                      + " plus récent.")
          List<FeelingPointDto> points,
      @Schema(
              description =
                  "Une série par émotion présente dans la fenêtre, par nombre de ressentis"
                      + " décroissant puis par code.")
          List<FeelingSeriesDto> series,
      @Schema(description = "Repères de médicament (début, fin) de la fenêtre.")
          List<TrendMarkerDto> markers,
      @Schema(
              description =
                  "Jours UTC des séances de la fenêtre, croissants, sans doublon (AAAA-MM-JJ).")
          List<LocalDate> ticks) {}

  @Schema(description = "Un ressenti : une valeur par point.")
  record FeelingPointDto(
      @Schema(
              description = "Identifiant de l'entrée ressenti.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID entryId,
      @Schema(
              description =
                  "Jour UTC de placement du point, AAAA-MM-JJ : le jour du ressenti, ou le milieu"
                      + " de la période pour un ressenti imprécis, plafonné à aujourd'hui.",
              example = "2026-03-12")
          LocalDate date,
      @Schema(
              description = "Début du ressenti (occurred_from), UTC ISO 8601.",
              example = "2026-03-12T20:15:00Z")
          Instant at,
      @Schema(description = "Code de l'émotion ressentie.", example = "anxiety") String emotionCode,
      @Schema(
              description =
                  "Famille de l'émotion (joy, trust, fear, surprise, sadness, disgust, anger,"
                      + " anticipation) ; null si l'émotion n'est plus au catalogue.",
              example = "fear")
          String family,
      @Schema(
              description = "Intensité du ressenti, de 0 à 10.",
              minimum = "0",
              maximum = "10",
              example = "7")
          int intensity,
      @Schema(
              description =
                  "Valence de l'émotion, de -2 (très négative) à 2 (très positive) ; null si"
                      + " inconnue.",
              minimum = "-2",
              maximum = "2",
              example = "-1")
          Integer valence,
      @Schema(
              description = "Identifiant de la séance de rattachement.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID sessionId) {}

  @Schema(description = "Série de la courbe : une émotion de la fenêtre.")
  record FeelingSeriesDto(
      @Schema(description = "Code de l'émotion.", example = "anxiety") String emotionCode,
      @Schema(description = "Libellé français.", example = "Anxiété") String label,
      @Schema(description = "Libellé anglais ; null s'il n'existe pas.", example = "Anxiety")
          String labelEn,
      @Schema(
              description = "Famille de l'émotion ; null si elle n'est plus au catalogue.",
              example = "fear")
          String family,
      @Schema(description = "Nombre de ressentis de cette émotion dans la fenêtre.", example = "8")
          int count) {}

  @Schema(description = "Entrées postérieures à la dernière séance clôturée.")
  record SinceLastSessionDto(
      @Schema(
              description = "Dernière séance clôturée non archivée ; null s'il n'y en a pas.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID lastSessionId,
      @Schema(
              description =
                  "Début de cette séance (occurred_from), UTC ISO 8601 ; null s'il n'y en a pas.",
              example = "2026-03-07T09:00:00Z")
          Instant lastSessionAt,
      @Schema(
              description =
                  "20 entrées au plus, de la plus récente à la plus ancienne : non archivées, hors"
                      + " séances, non rattachées à la dernière séance, commençant à son début ou"
                      + " après. Sans séance clôturée : les 20 dernières entrées hors séances.")
          List<SinceLastSessionEntryDto> entries) {}

  @Schema(description = "Entrée saisie depuis la dernière séance clôturée.")
  record SinceLastSessionEntryDto(
      @Schema(
              description = "Identifiant de l'entrée.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID entryId,
      @Schema(
              description = "Type d'entrée.",
              allowableValues = {"note", "event", "situation", "feeling", "medication"},
              example = "note")
          String type,
      @Schema(
              description = "Titre de la version courante ; null si l'entrée n'a pas de titre.",
              example = "Rêve récurrent")
          String title,
      @Schema(
              description = "Début (occurred_from), UTC ISO 8601.",
              example = "2026-03-10T18:00:00Z")
          Instant occurredFrom,
      @Schema(
              description = "État de l'entrée : draft = brouillon, final = validée.",
              allowableValues = {"draft", "final"},
              example = "final")
          String status,
      @Schema(
              description =
                  "Visibilité : private = réservée au praticien, clinical = dossier clinique.",
              allowableValues = {"private", "clinical"},
              example = "clinical")
          String visibility,
      @Schema(
              description = "Identifiant de la séance de rattachement.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID sessionId) {}

  @Schema(description = "Séance récente ; seule la plus récente porte le compte rendu entier.")
  record RecentSessionDto(
      @Schema(
              description = "Identifiant de l'entrée séance.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          UUID entryId,
      @Schema(
              description = "Début de la séance (occurred_from), UTC ISO 8601.",
              example = "2026-03-07T09:00:00Z")
          Instant occurredFrom,
      @Schema(
              description =
                  "Nature : intake = accueil, session = séance, contact = hors rendez-vous.",
              allowableValues = {"intake", "session", "contact"},
              example = "session")
          String kind,
      @Schema(
              description = "Modalité : en cabinet, en visio, par téléphone ou par e-mail.",
              allowableValues = {"in_person", "video", "phone", "email"},
              example = "in_person")
          String modality,
      @Schema(description = "Durée en minutes (1 à 600) ; null si non renseignée.", example = "60")
          Integer durationMin,
      @Schema(
              description = "Date-heure de clôture, UTC ISO 8601 ; null si la séance est ouverte.",
              example = "2026-03-07T10:00:00Z")
          Instant closedAt,
      @Schema(
              description = "Titre de la séance ; null si elle n'en a pas.",
              example = "Séance de suivi")
          String title,
      @Schema(description = "Nombre d'entrées non archivées rattachées à la séance.", example = "4")
          int entryCount,
      @Schema(
              description =
                  "Première ligne non vide du compte rendu (200 caractères au plus, « … » si"
                      + " tronquée) ; null sans compte rendu.",
              example = "Anxiété en baisse depuis la reprise du traitement.")
          String summaryFirstLine,
      @Schema(
              description =
                  "Compte rendu entier, pour la séance la plus récente seulement ; null pour les"
                      + " autres ou sans compte rendu.")
          String summary) {}
}
