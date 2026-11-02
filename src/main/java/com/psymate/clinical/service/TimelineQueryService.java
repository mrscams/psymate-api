package com.psymate.clinical.service;

import com.psymate.clinical.model.EntryType;
import com.psymate.clinical.model.TimeSpan;
import com.psymate.clinical.model.TimelineFacets;
import com.psymate.clinical.model.TimelineItem;
import com.psymate.clinical.model.TimelinePage;
import com.psymate.clinical.model.TimelineSummary;
import com.psymate.clinical.repository.TimelineRepository;
import com.psymate.clinical.repository.TimelineRepository.Filter;
import com.psymate.clinical.repository.TimelineRepository.PeriodFeelings;
import com.psymate.clinical.repository.TimelineRepository.PeriodTypeCount;
import com.psymate.clinical.repository.TimelineRepository.Row;
import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.TagFacade;
import com.psymate.vocabulary.api.TagView;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Timeline d'un patient (A6) : fenêtre keyset, facettes, situations qui chevauchent la fenêtre, et
 * résumé par mois ou par année. La propriété du patient est vérifiée par l'appelant (garde de
 * propriété) avant tout appel.
 */
@Service
@Transactional(readOnly = true)
public class TimelineQueryService {

  private final TimelineRepository repo;
  private final TagFacade tags;
  private final EmotionCatalog emotions;

  TimelineQueryService(TimelineRepository repo, TagFacade tags, EmotionCatalog emotions) {
    this.repo = repo;
    this.tags = tags;
    this.emotions = emotions;
  }

  /** Une page de la fenêtre, avec facettes de toute la fenêtre et situations chevauchantes. */
  public TimelinePage timeline(UUID patientId, TimelineQuery query) {
    Filter filter = filter(patientId, query);
    TimelineCursor cursor = query.cursor();
    List<Row> fetched =
        repo.page(
            filter,
            cursor == null ? null : cursor.occurredFrom(),
            cursor == null ? null : cursor.id(),
            query.limit() + 1);
    boolean hasMore = fetched.size() > query.limit();
    List<Row> pageRows = hasMore ? fetched.subList(0, query.limit()) : fetched;
    List<Row> ongoingRows = repo.overlappingSituations(filter);

    Map<String, String> families = familyByCode();
    Map<UUID, List<UUID>> tagIdsByEntry = tagIdsOf(pageRows, ongoingRows);
    Map<UUID, String> labels = labelsOf(tagIdsByEntry.values());

    List<TimelineItem> items =
        pageRows.stream().map(r -> item(r, families, tagIdsByEntry, labels)).toList();
    List<TimelineItem> ongoing =
        ongoingRows.stream().map(r -> item(r, families, tagIdsByEntry, labels)).toList();

    String nextCursor = null;
    if (hasMore) {
      Row last = pageRows.get(pageRows.size() - 1);
      nextCursor = new TimelineCursor(last.occurredFrom(), last.id()).encode();
    }
    return new TimelinePage(items, nextCursor, facets(filter), ongoing);
  }

  /** Agrégats par période UTC : comptes par type, ressentis et intensité moyenne. */
  public TimelineSummary summary(
      UUID patientId,
      TimelineGranularity granularity,
      Instant from,
      Instant to,
      boolean includePrivate) {
    Filter filter = new Filter(patientId, from, to, null, null, null, false, includePrivate);
    String unit = granularity.code();
    Map<LocalDateTime, Map<String, Integer>> counts = new TreeMap<>();
    for (PeriodTypeCount c : repo.countsByPeriod(filter, unit)) {
      counts.computeIfAbsent(c.period(), k -> new LinkedHashMap<>()).put(c.type(), c.count());
    }
    Map<LocalDateTime, PeriodFeelings> feelings = new HashMap<>();
    for (PeriodFeelings f : repo.feelingsByPeriod(filter, unit)) {
      feelings.put(f.period(), f);
    }
    List<TimelineSummary.Period> periods = new ArrayList<>();
    counts.forEach(
        (start, byType) -> {
          PeriodFeelings f = feelings.get(start);
          periods.add(
              new TimelineSummary.Period(
                  label(granularity, start),
                  TimelineRepository.utc(start),
                  byType,
                  f == null ? 0 : f.count(),
                  f == null ? null : f.averageIntensity()));
        });
    return new TimelineSummary(unit, periods);
  }

  private static String label(TimelineGranularity granularity, LocalDateTime start) {
    return granularity == TimelineGranularity.YEAR
        ? "%04d".formatted(start.getYear())
        : "%04d-%02d".formatted(start.getYear(), start.getMonthValue());
  }

  // ---- filtres

  private Filter filter(UUID patientId, TimelineQuery q) {
    List<String> types =
        q.types() == null ? null : q.types().stream().map(EntryType::code).toList();
    return new Filter(
        patientId,
        q.from(),
        q.to(),
        types,
        q.emotions(),
        tagIds(q.tags()),
        q.draftsOnly(),
        q.includePrivate());
  }

  /** Nul sans filtre de tags ; sinon les ids des libellés connus (vide : rien ne correspond). */
  private List<UUID> tagIds(List<String> labels) {
    if (labels == null || labels.isEmpty()) {
      return null;
    }
    Set<UUID> ids = new HashSet<>();
    for (String label : labels) {
      Optional<TagView> tag = tags.resolve(label);
      tag.ifPresent(t -> ids.add(t.id()));
    }
    return List.copyOf(ids);
  }

  // ---- facettes

  private TimelineFacets facets(Filter filter) {
    Map<String, Integer> byTag = new LinkedHashMap<>();
    Map<UUID, Integer> tagCounts = repo.tagFacet(filter);
    if (!tagCounts.isEmpty()) {
      Map<UUID, String> labels = tags.labelsByIds(tagCounts.keySet());
      tagCounts.forEach(
          (id, n) -> {
            String label = labels.get(id);
            if (label != null) {
              byTag.merge(label, n, Integer::sum);
            }
          });
    }
    return new TimelineFacets(repo.typeFacet(filter), repo.emotionFacet(filter), byTag);
  }

  // ---- éléments

  /** Famille par code d'émotion, calculée une fois par requête (la famille peut être nulle). */
  private Map<String, String> familyByCode() {
    Map<String, String> byCode = new HashMap<>();
    for (EmotionView e : emotions.activeEmotions()) {
      byCode.putIfAbsent(e.code(), e.family());
    }
    return byCode;
  }

  /** Tags de toutes les entrées de la page et des situations, en une requête. */
  private Map<UUID, List<UUID>> tagIdsOf(List<Row> page, List<Row> ongoing) {
    Set<UUID> ids = new HashSet<>();
    page.forEach(r -> ids.add(r.id()));
    ongoing.forEach(r -> ids.add(r.id()));
    return repo.tagIdsByEntry(ids);
  }

  private Map<UUID, String> labelsOf(Collection<List<UUID>> tagIds) {
    Set<UUID> all = new HashSet<>();
    tagIds.forEach(all::addAll);
    return all.isEmpty() ? Map.of() : tags.labelsByIds(all);
  }

  private static TimelineItem item(
      Row r,
      Map<String, String> families,
      Map<UUID, List<UUID>> tagIdsByEntry,
      Map<UUID, String> labels) {
    List<String> tagLabels =
        tagIdsByEntry.getOrDefault(r.id(), List.of()).stream()
            .map(labels::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(String::toLowerCase))
            .toList();
    TimeSpan span =
        new TimeSpan(
            r.occurredFrom(),
            r.occurredTo(),
            TimeSpan.Precision.fromCode(r.precision()),
            r.label());
    return new TimelineItem(
        r.id(),
        r.type(),
        span,
        r.title(),
        r.emotionCode(),
        r.emotionCode() == null ? null : families.get(r.emotionCode()),
        r.intensity(),
        tagLabels,
        r.linkCount(),
        r.sessionId(),
        r.status(),
        r.visibility(),
        r.authorType(),
        r.recordedAt(),
        EntryType.SITUATION.code().equals(r.type()) && r.occurredTo() == null);
  }
}
