package com.psymate.clinical.web;

import com.psymate.clinical.web.EntryDtos.EntryOccurredDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTO REST de la timeline (A5, A6) : éléments allégés, facettes, résumé par période. */
final class TimelineDtos {

  private TimelineDtos() {}

  /** Émotion d'un ressenti : code et famille d'après le catalogue (nulle si inconnue). */
  @Schema(description = "Émotion d'un ressenti d'après le catalogue de vocabulaire du praticien.")
  record TimelineEmotionDto(
      @Schema(description = "Code de l'émotion.", example = "anxiety") String code,
      @Schema(
              description =
                  "Famille de l'émotion d'après le catalogue : joy, trust, fear, surprise, sadness,"
                      + " disgust, anger ou anticipation. Null si elle est inconnue.",
              example = "fear")
          String family) {}

  /**
   * Élément de timeline. {@code authorType} est en lecture seule, toujours « practitioner » en V1
   * (A11). {@code ongoing} : situation sans fin.
   */
  @Schema(
      description =
          "Entrée allégée de la chronologie : de quoi dessiner une ligne sans charger l'entrée"
              + " complète (GET /api/entries/{id}).")
  record TimelineItemDto(
      @Schema(description = "Identifiant de l'entrée.") UUID id,
      @Schema(
              description = "Type de l'entrée.",
              allowableValues = {"note", "event", "situation", "feeling", "session", "medication"},
              example = "feeling")
          String type,
      @Schema(description = "Moment auquel l'entrée a eu lieu, avec sa précision.")
          EntryOccurredDto occurred,
      @Schema(description = "Titre de la version courante. Null si la version n'en a pas.")
          String title,
      @Schema(description = "Émotion du ressenti. Renseignée pour le type feeling, null sinon.")
          TimelineEmotionDto emotion,
      @Schema(
              description = "Intensité du ressenti, de 0 à 10. Null pour les autres types.",
              minimum = "0",
              maximum = "10")
          Integer intensity,
      @Schema(
              description =
                  "Libellés des tags actifs de l'entrée, triés sans tenir compte de la casse.")
          List<String> tags,
      @Schema(description = "Nombre de liens acceptés portant sur l'entrée.", minimum = "0")
          int linkCount,
      @Schema(
              description =
                  "Identifiant de la séance de l'entrée. Null pour une séance (une séance n'est"
                      + " rattachée à aucune séance).")
          UUID sessionId,
      @Schema(
              description = "Statut : draft = brouillon ; final = définitive.",
              allowableValues = {"draft", "final"},
              example = "final")
          String status,
      @Schema(
              description =
                  "Visibilité : private = visible du seul praticien auteur ; clinical = partagée"
                      + " avec l'équipe clinique.",
              allowableValues = {"private", "clinical"},
              example = "clinical")
          String visibility,
      @Schema(
              description =
                  "Provenance de l'entrée. Lecture seule : vaut toujours practitioner en V1.",
              accessMode = Schema.AccessMode.READ_ONLY,
              allowableValues = {"practitioner"},
              example = "practitioner")
          String authorType,
      @Schema(
              description = "Instant d'enregistrement de l'entrée, UTC ISO 8601.",
              example = "2026-10-20T19:05:00Z")
          Instant recordedAt,
      @Schema(
              description =
                  "Vrai pour une situation sans fin (occurred.to nul), toujours en cours. Faux"
                      + " pour tout autre élément.")
          boolean ongoing) {}

  /** Comptes de toute la fenêtre filtrée (pas seulement la page), clés = code ou libellé. */
  @Schema(
      description =
          "Comptes de toute la fenêtre filtrée (et non de la seule page), indépendants du curseur."
              + " Triés du plus fréquent au plus rare.")
  record TimelineFacetsDto(
      @Schema(description = "Nombre d'entrées par type (clé = code du type).")
          Map<String, Integer> types,
      @Schema(description = "Nombre de ressentis par émotion (clé = code de l'émotion).")
          Map<String, Integer> emotions,
      @Schema(description = "Nombre d'entrées par tag (clé = libellé du tag).")
          Map<String, Integer> tags) {}

  /**
   * Page de timeline : triée par occurred_from décroissant, nextCursor nul sur la dernière page,
   * situations en cours qui chevauchent la fenêtre à part.
   */
  @Schema(
      description =
          "Page de chronologie, triée par début d'occurrence décroissant (puis identifiant"
              + " décroissant), avec facettes de la fenêtre et situations en cours.")
  record TimelinePageDto(
      @Schema(description = "Entrées de la page, de la plus récente à la plus ancienne.")
          List<TimelineItemDto> items,
      @Schema(
              description =
                  "Curseur opaque à renvoyer tel quel dans le paramètre cursor pour obtenir la page"
                      + " suivante. Null sur la dernière page.")
          String nextCursor,
      @Schema(description = "Facettes de toute la fenêtre filtrée.") TimelineFacetsDto facets,
      @Schema(
              description =
                  "Situations non archivées dont la plage chevauche la fenêtre, y compris celles"
                      + " commencées avant from et toujours en cours. Seuls les filtres de"
                      + " fenêtre, de brouillon et de visibilité s'appliquent (pas types, emotions"
                      + " ni tags) ; indépendantes de la pagination.")
          List<TimelineItemDto> ongoingSituations) {}

  /** Une période du résumé : « 2026-10 » (mois) ou « 2026 » (année), début en UTC. */
  @Schema(description = "Agrégats d'une période (un mois ou une année, en UTC).")
  record TimelinePeriodDto(
      @Schema(
              description = "Libellé de la période : AAAA-MM pour un mois, AAAA pour une année.",
              example = "2026-10")
          String period,
      @Schema(description = "Début de la période, UTC ISO 8601.", example = "2026-10-01T00:00:00Z")
          Instant from,
      @Schema(description = "Nombre d'entrées de la période par type (clé = code du type).")
          Map<String, Integer> counts,
      @Schema(description = "Nombre de ressentis de la période.", minimum = "0") int feelingCount,
      @Schema(
              description =
                  "Intensité moyenne des ressentis de la période. Null s'il n'y en a pas.")
          Double averageIntensity) {}

  @Schema(
      description =
          "Résumé de la chronologie par mois ou par année. Seules les périodes qui contiennent au"
              + " moins une entrée sont renvoyées, de la plus ancienne à la plus récente.")
  record TimelineSummaryDto(
      @Schema(
              description = "Granularité demandée.",
              allowableValues = {"month", "year"},
              example = "month")
          String granularity,
      @Schema(description = "Périodes non vides, de la plus ancienne à la plus récente.")
          List<TimelinePeriodDto> periods) {}
}
