package com.psymate.overview;

import com.psymate.overview.OverviewDtos.FeelingPointDto;
import com.psymate.overview.OverviewDtos.FeelingSeriesDto;
import com.psymate.overview.OverviewDtos.FeelingsDto;
import com.psymate.overview.OverviewDtos.TrendMarkerDto;
import com.psymate.overview.OverviewPayload.EmotionRef;
import com.psymate.overview.OverviewPayload.StoredFeelingPoint;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Découpe, pour une fenêtre, les ressentis réels stockés sur 12 mois : un point par ressenti dont
 * le jour tombe dans la fenêtre, une série par émotion présente, les repères de médicament et les
 * jours de séance de la tendance de la fenêtre. Calcul pur, sans accès à la base.
 */
final class FeelingsAssembler {

  private FeelingsAssembler() {}

  static FeelingsDto assemble(OverviewPayload payload, OverviewWindow window, LocalDate today) {
    LocalDate start = window.start(today);
    Map<String, EmotionRef> catalog = new HashMap<>();
    for (EmotionRef e : payload.emotionCatalog()) {
      catalog.put(e.code(), e);
    }
    List<StoredFeelingPoint> inWindow =
        payload.feelingPoints().stream()
            .filter(p -> !p.date().isBefore(start) && !p.date().isAfter(today))
            .toList();
    List<FeelingPointDto> points =
        inWindow.stream()
            .map(
                p ->
                    new FeelingPointDto(
                        p.entryId(),
                        p.date(),
                        p.at(),
                        p.emotionCode(),
                        family(catalog, p.emotionCode()),
                        p.intensity(),
                        p.valence(),
                        p.sessionId()))
            .toList();

    Map<String, Integer> counts = new HashMap<>();
    for (StoredFeelingPoint p : inWindow) {
      counts.merge(p.emotionCode(), 1, Integer::sum);
    }
    List<FeelingSeriesDto> series =
        counts.entrySet().stream()
            .map(
                e -> {
                  EmotionRef ref = catalog.get(e.getKey());
                  return new FeelingSeriesDto(
                      e.getKey(),
                      ref == null ? e.getKey() : ref.labelFr(),
                      ref == null ? null : ref.labelEn(),
                      ref == null ? null : ref.family(),
                      e.getValue());
                })
            .sorted(
                Comparator.comparingInt(FeelingSeriesDto::count)
                    .reversed()
                    .thenComparing(FeelingSeriesDto::emotionCode))
            .toList();

    List<TrendMarkerDto> markers = payload.windows().get(window.code()).trend().markers();
    List<TrendMarkerDto> medication =
        markers.stream().filter(m -> "medication".equals(m.kind())).toList();
    List<LocalDate> ticks =
        markers.stream()
            .filter(m -> "session".equals(m.kind()))
            .map(TrendMarkerDto::date)
            .distinct()
            .sorted()
            .toList();
    return new FeelingsDto(points, series, medication, ticks);
  }

  private static String family(Map<String, EmotionRef> catalog, String code) {
    EmotionRef ref = catalog.get(code);
    return ref == null ? null : ref.family();
  }
}
