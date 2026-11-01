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

  @Schema(description = "Vue d'ensemble d'un patient pour une fenêtre (30d, 90d ou 12m).")
  record OverviewDto(
      @Schema(description = "Fenêtre appliquée à trend et emotions : 30d, 90d ou 12m")
          String window,
      OverviewFactsDto facts,
      @Schema(description = "Situations en cours, triées par nombre de liens décroissant")
          List<ActiveSituationDto> activeSituations,
      TrendDto trend,
      EmotionsDto emotions,
      DraftsDto drafts,
      @Schema(description = "10 dernières entrées non archivées, lues en direct (hors projection)")
          List<RecentActivityItemDto> recentActivity,
      @Schema(description = "Résumé « En bref » rédigé par règles, dans la langue de la requête")
          NarrativeDto narrative,
      @Schema(description = "Ressentis réels de la fenêtre : un point par ressenti")
          FeelingsDto feelings,
      @Schema(description = "Entrées saisies depuis la dernière séance clôturée")
          SinceLastSessionDto sinceLastSession,
      @Schema(description = "5 dernières séances, la plus récente avec son compte rendu entier")
          List<RecentSessionDto> recentSessions,
      @Schema(description = "Instant (UTC) du calcul de la projection") Instant computedAt,
      @Schema(description = "patient.clinical_rev au moment du calcul de la projection")
          long sourceRev) {}

  @Schema(description = "Faits clés du patient")
  record OverviewFactsDto(
      @Schema(description = "Âge en années, null sans date de naissance") Integer age,
      @Schema(description = "follow_up.since, sinon la date de création du dossier")
          LocalDate followedSince,
      @Schema(description = "Nombre de séances non archivées") int sessionCount,
      List<ActiveMedicationDto> activeMedications,
      @Schema(description = "Drapeaux actifs (non levés)") List<OverviewFlagDto> flags) {}

  @Schema(description = "Médicament en cours")
  record ActiveMedicationDto(
      UUID entryId,
      String molecule,
      String dosage,
      String frequency,
      String prescriber,
      @Schema(description = "Début de l'entrée (occurred_from)") Instant since,
      @Schema(description = "Début de la période de prise, si saisi") LocalDate periodFrom,
      @Schema(description = "Fin de la période de prise, si saisie (nulle = en cours)")
          LocalDate periodTo) {}

  @Schema(description = "Drapeau actif posé à la main sur le dossier")
  record OverviewFlagDto(
      @Schema(description = "watch, risk ou shared_care") String kind,
      String reason,
      Instant setAt) {}

  @Schema(description = "Situation en cours (occurred_to nul)")
  record ActiveSituationDto(
      UUID entryId,
      String title,
      @Schema(description = "Début (occurred_from)") Instant since,
      int linkCount,
      @Schema(
              description =
                  "Ressentis non archivés reliés par un lien accepté, dans un sens ou l'autre")
          int linkedFeelingCount,
      @Schema(description = "Moyenne des intensités de ces ressentis, null s'il n'y en a aucun")
          Double averageIntensity) {}

  @Schema(description = "Courbe d'intensité et repères de la fenêtre")
  record TrendDto(List<TrendPointDto> points, List<TrendMarkerDto> markers) {}

  @Schema(description = "Point de la courbe : moyenne glissante sur 7 jours à cette date")
  record TrendPointDto(
      LocalDate date,
      @Schema(
              description =
                  "Intensité moyenne (0 à 10) des ressentis des 7 derniers jours, null si aucun")
          Double averageIntensity,
      @Schema(description = "Valence moyenne (-2 à 2) des valences connues, null sinon")
          Double valence) {}

  @Schema(description = "Repère sur la courbe : séance ou changement de médicament")
  record TrendMarkerDto(
      LocalDate date,
      @Schema(description = "session ou medication") String kind,
      UUID entryId,
      @Schema(description = "Titre de la séance (null sans titre) ou « molécule dosage »")
          String label,
      @Schema(description = "intake, session ou contact pour une séance, null pour un médicament")
          String sessionKind,
      @Schema(description = "start ou end pour un médicament, null pour une séance")
          String change) {}

  @Schema(description = "Émotions dominantes de la fenêtre")
  record EmotionsDto(
      @Schema(description = "5 au plus, par effectif décroissant") List<TopEmotionDto> top) {}

  @Schema(description = "Émotion dominante et variation par rapport à la fenêtre précédente")
  record TopEmotionDto(
      String code,
      String label,
      @Schema(description = "Nombre de ressentis dans la fenêtre") int count,
      @Schema(description = "Nombre dans la fenêtre précédente de même longueur") int previousCount,
      @Schema(description = "count moins previousCount") int delta) {}

  @Schema(description = "Brouillons et séance ouverte")
  record DraftsDto(
      @Schema(description = "Entrées en brouillon non archivées") int count,
      @Schema(description = "Séance non clôturée la plus récente, null s'il n'y en a pas")
          OpenSessionDto openSession) {}

  @Schema(description = "Séance ouverte")
  record OpenSessionDto(UUID entryId, Instant startedAt) {}

  @Schema(description = "Entrée récente")
  record RecentActivityItemDto(
      UUID entryId,
      String type,
      String title,
      Instant occurredFrom,
      Instant occurredTo,
      @Schema(description = "draft ou final") String status,
      @Schema(description = "private ou clinical") String visibility,
      UUID sessionId,
      @Schema(description = "Version courante") int version,
      Instant recordedAt) {}

  @Schema(
      description =
          "Résumé rédigé par règles (jusqu'à 5 phrases, chacune omise si ses données manquent,"
              + " sans IA), dans la langue de la requête"
              + " (Accept-Language : français par défaut, anglais si demandé)")
  record NarrativeDto(
      @Schema(
              description =
                  "Phrases dans l'ordre cadre, tendance, situations, événement, traitement ;"
                      + " chacune est omise si ses données manquent")
          List<String> sentences,
      @Schema(description = "Point d'attention issu des drapeaux actifs, null sans drapeau")
          String attention) {}

  @Schema(description = "Ressentis réels de la fenêtre, repères de médicament et jours de séance")
  record FeelingsDto(
      @Schema(description = "Exactement un point par ressenti non archivé de la fenêtre")
          List<FeelingPointDto> points,
      @Schema(
              description =
                  "Une entrée par émotion présente dans la fenêtre, par effectif décroissant")
          List<FeelingSeriesDto> series,
      @Schema(description = "Repères de médicament (début, fin) de la fenêtre")
          List<TrendMarkerDto> markers,
      @Schema(description = "Jours des séances de la fenêtre, croissants, sans doublon")
          List<LocalDate> ticks) {}

  @Schema(description = "Un ressenti : une valeur par point")
  record FeelingPointDto(
      UUID entryId,
      @Schema(description = "Jour UTC de placement (milieu de période, plafonné à aujourd'hui)")
          LocalDate date,
      @Schema(description = "occurred_from de l'entrée") Instant at,
      String emotionCode,
      @Schema(description = "Famille de l'émotion (joy, trust, fear, ...)") String family,
      @Schema(description = "0 à 10") int intensity,
      @Schema(description = "-2 à 2, null si inconnue") Integer valence,
      @Schema(description = "Séance de rattachement") UUID sessionId) {}

  @Schema(description = "Série de la courbe : une émotion de la fenêtre")
  record FeelingSeriesDto(
      String emotionCode,
      @Schema(description = "Libellé français") String label,
      @Schema(description = "Libellé anglais, null s'il n'existe pas") String labelEn,
      String family,
      @Schema(description = "Nombre de ressentis de cette émotion dans la fenêtre") int count) {}

  @Schema(description = "Entrées postérieures à la dernière séance clôturée")
  record SinceLastSessionDto(
      @Schema(description = "Dernière séance clôturée non archivée, null s'il n'y en a pas")
          UUID lastSessionId,
      @Schema(description = "occurred_from de cette séance, null s'il n'y en a pas")
          Instant lastSessionAt,
      @Schema(description = "20 au plus, de la plus récente à la plus ancienne")
          List<SinceLastSessionEntryDto> entries) {}

  @Schema(description = "Entrée saisie depuis la dernière séance clôturée")
  record SinceLastSessionEntryDto(
      UUID entryId,
      String type,
      String title,
      Instant occurredFrom,
      @Schema(description = "draft ou final") String status,
      @Schema(description = "private ou clinical") String visibility,
      UUID sessionId) {}

  @Schema(description = "Séance récente ; seule la plus récente porte le compte rendu entier")
  record RecentSessionDto(
      UUID entryId,
      Instant occurredFrom,
      @Schema(description = "intake, session ou contact") String kind,
      @Schema(description = "in_person, video, phone ou email") String modality,
      Integer durationMin,
      @Schema(description = "Clôture, null si la séance est ouverte") Instant closedAt,
      String title,
      @Schema(description = "Entrées non archivées rattachées à la séance") int entryCount,
      @Schema(description = "Première ligne du compte rendu, null sans compte rendu")
          String summaryFirstLine,
      @Schema(description = "Compte rendu entier (séance la plus récente seulement), sinon null")
          String summary) {}
}
