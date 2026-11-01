package com.psymate.overview;

import com.psymate.overview.OverviewDtos.ActiveSituationDto;
import com.psymate.overview.OverviewDtos.DraftsDto;
import com.psymate.overview.OverviewDtos.EmotionsDto;
import com.psymate.overview.OverviewDtos.OverviewFactsDto;
import com.psymate.overview.OverviewDtos.TrendDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Contenu de {@code patient_overview.payload}. {@code patientChangedAt} est la date du dernier
 * changement de la fiche ou des drapeaux, relevée dans la base au calcul : comparée à la valeur
 * courante, elle détecte une fiche modifiée sans que {@code clinical_rev} bouge. {@code facts},
 * {@code activeSituations} et {@code drafts} ne dépendent pas de la fenêtre ; {@code windows}
 * porte, pour chacune des clés {@code 30d}, {@code 90d} et {@code 12m}, la tendance et les émotions
 * dominantes. Le contrôleur en extrait la fenêtre demandée.
 */
record OverviewPayload(
    Instant patientChangedAt,
    OverviewFactsDto facts,
    List<ActiveSituationDto> activeSituations,
    DraftsDto drafts,
    Map<String, WindowBlock> windows) {

  /** Blocs dépendant de la fenêtre. */
  record WindowBlock(TrendDto trend, EmotionsDto emotions) {}
}
