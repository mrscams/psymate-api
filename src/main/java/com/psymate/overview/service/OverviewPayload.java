package com.psymate.overview.service;

import com.psymate.overview.dto.ActiveSituationDto;
import com.psymate.overview.dto.DraftsDto;
import com.psymate.overview.dto.EmotionsDto;
import com.psymate.overview.dto.OverviewFactsDto;
import com.psymate.overview.dto.RecentSessionDto;
import com.psymate.overview.dto.SinceLastSessionDto;
import com.psymate.overview.dto.TrendDto;
import com.psymate.overview.model.NarrativeFacts;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contenu de {@code patient_overview.payload}. {@code patientChangedAt} est la date du dernier
 * changement de la fiche ou des drapeaux, relevée dans la base au calcul : comparée à la valeur
 * courante, elle détecte une fiche modifiée sans que {@code clinical_rev} bouge. {@code facts},
 * {@code activeSituations} et {@code drafts} ne dépendent pas de la fenêtre ; {@code windows}
 * porte, pour chacune des clés {@code 30d}, {@code 90d} et {@code 12m}, la tendance et les émotions
 * dominantes. Le service en extrait la fenêtre demandée.
 *
 * <p>M4-06 ajoute, indépendamment de la fenêtre : {@code narrativeFacts} (données du résumé, jamais
 * de phrase : le texte se compose à la lecture dans la langue de la requête), {@code feelingPoints}
 * (un point par ressenti des 12 derniers mois, filtré par fenêtre à la lecture) avec son {@code
 * emotionCatalog}, {@code sinceLastSession} et {@code recentSessions}. Une ligne écrite avant M4-06
 * n'a pas ces champs : {@link #hasAllBlocks()} la déclare incomplète et le service la recalcule.
 */
record OverviewPayload(
    Instant patientChangedAt,
    OverviewFactsDto facts,
    List<ActiveSituationDto> activeSituations,
    DraftsDto drafts,
    Map<String, WindowBlock> windows,
    NarrativeFacts narrativeFacts,
    List<StoredFeelingPoint> feelingPoints,
    List<EmotionRef> emotionCatalog,
    SinceLastSessionDto sinceLastSession,
    List<RecentSessionDto> recentSessions) {

  /** Blocs dépendant de la fenêtre. */
  record WindowBlock(TrendDto trend, EmotionsDto emotions) {}

  /** Ressenti stocké (le libellé et la famille de l'émotion sont dans {@code emotionCatalog}). */
  record StoredFeelingPoint(
      UUID entryId,
      LocalDate date,
      Instant at,
      String emotionCode,
      int intensity,
      Integer valence,
      UUID sessionId) {}

  /** Émotion présente parmi les points stockés. */
  record EmotionRef(String code, String family, String labelFr, String labelEn) {}

  /** Vrai si tous les blocs de la version courante du calcul sont présents. */
  boolean hasAllBlocks() {
    return windows != null
        && narrativeFacts != null
        && feelingPoints != null
        && emotionCatalog != null
        && sinceLastSession != null
        && recentSessions != null;
  }
}
