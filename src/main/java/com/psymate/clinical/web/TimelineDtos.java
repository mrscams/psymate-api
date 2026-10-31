package com.psymate.clinical.web;

import com.psymate.clinical.web.EntryDtos.EntryOccurredDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTO REST de la timeline (A5, A6) : éléments allégés, facettes, résumé par période. */
final class TimelineDtos {

  private TimelineDtos() {}

  /** Émotion d'un ressenti : code et famille d'après le catalogue (nulle si inconnue). */
  record TimelineEmotionDto(String code, String family) {}

  /**
   * Élément de timeline. {@code authorType} est en lecture seule, toujours « practitioner » en V1
   * (A11). {@code ongoing} : situation sans fin.
   */
  record TimelineItemDto(
      UUID id,
      String type,
      EntryOccurredDto occurred,
      String title,
      TimelineEmotionDto emotion,
      Integer intensity,
      List<String> tags,
      int linkCount,
      UUID sessionId,
      String status,
      String visibility,
      String authorType,
      Instant recordedAt,
      boolean ongoing) {}

  /** Comptes de toute la fenêtre filtrée (pas seulement la page), clés = code ou libellé. */
  record TimelineFacetsDto(
      Map<String, Integer> types, Map<String, Integer> emotions, Map<String, Integer> tags) {}

  /**
   * Page de timeline : triée par occurred_from décroissant, nextCursor nul sur la dernière page,
   * situations en cours qui chevauchent la fenêtre à part.
   */
  record TimelinePageDto(
      List<TimelineItemDto> items,
      String nextCursor,
      TimelineFacetsDto facets,
      List<TimelineItemDto> ongoingSituations) {}

  /** Une période du résumé : « 2026-10 » (mois) ou « 2026 » (année), début en UTC. */
  record TimelinePeriodDto(
      String period,
      Instant from,
      Map<String, Integer> counts,
      int feelingCount,
      Double averageIntensity) {}

  record TimelineSummaryDto(String granularity, List<TimelinePeriodDto> periods) {}
}
