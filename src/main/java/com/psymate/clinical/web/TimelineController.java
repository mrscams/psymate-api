package com.psymate.clinical.web;

import com.psymate.clinical.application.TimelineCursor;
import com.psymate.clinical.application.TimelineGranularity;
import com.psymate.clinical.application.TimelineQuery;
import com.psymate.clinical.application.TimelineQueryService;
import com.psymate.clinical.domain.EntryType;
import com.psymate.clinical.web.TimelineDtos.TimelinePageDto;
import com.psymate.clinical.web.TimelineDtos.TimelineSummaryDto;
import com.psymate.patients.api.PatientOwnershipGuard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Timeline d'un patient (A6) : fenêtre triée par occurred_from décroissant avec curseur keyset,
 * facettes de la fenêtre, situations qui la chevauchent ; résumé par mois ou par année.
 */
@RestController
class TimelineController {

  static final int MAX_LIMIT = 200;

  private final TimelineQueryService timeline;
  private final TimelineMapper mapper;
  private final PatientOwnershipGuard guard;

  TimelineController(
      TimelineQueryService timeline, TimelineMapper mapper, PatientOwnershipGuard guard) {
    this.timeline = timeline;
    this.mapper = mapper;
    this.guard = guard;
  }

  /**
   * Fenêtre [from, to[ sur occurred_from, au plus {@code limit} éléments (200 max). Filtres types,
   * emotions et tags : un élément doit correspondre à l'un des codes d'une même liste. Les
   * brouillons sont inclus sauf {@code draftsOnly=true} (brouillons seuls) ; les entrées privées
   * sont exclues sauf {@code includePrivate=true}.
   */
  @GetMapping("/api/patients/{id}/timeline")
  TimelinePageDto getTimeline(
      @PathVariable UUID id,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) List<String> types,
      @RequestParam(required = false) List<String> emotions,
      @RequestParam(required = false) List<String> tags,
      @RequestParam(defaultValue = "false") boolean draftsOnly,
      @RequestParam(defaultValue = "false") boolean includePrivate,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "50") int limit) {
    guard.requireOwned(id);
    List<FieldProblem> problems = new ArrayList<>();
    if (limit < 1 || limit > MAX_LIMIT) {
      problems.add(new FieldProblem("limit", "OUT_OF_RANGE"));
    }
    if (from != null && to != null && from.isAfter(to)) {
      problems.add(new FieldProblem("from", "INVALID_TIME"));
    }
    List<EntryType> entryTypes = new ArrayList<>();
    for (String code : types == null ? List.<String>of() : types) {
      Optional<EntryType> type = EntryType.fromCode(code.strip());
      if (type.isPresent()) {
        entryTypes.add(type.get());
      } else {
        problems.add(new FieldProblem("types", "INVALID_VALUE"));
      }
    }
    TimelineCursor decoded = null;
    if (cursor != null && !cursor.isEmpty()) {
      try {
        decoded = TimelineCursor.decode(cursor);
      } catch (IllegalArgumentException e) {
        problems.add(new FieldProblem("cursor", "INVALID_VALUE"));
      }
    }
    if (!problems.isEmpty()) {
      throw new EntryRequestException(problems);
    }
    return mapper.toPage(
        timeline.timeline(
            id,
            new TimelineQuery(
                from, to, entryTypes, emotions, tags, draftsOnly, includePrivate, decoded, limit)));
  }

  /**
   * Agrégats par mois ou par année (UTC) : comptes par type, nombre de ressentis et intensité
   * moyenne. Brouillons inclus, entrées privées exclues sauf {@code includePrivate=true}.
   */
  @GetMapping("/api/patients/{id}/timeline/summary")
  TimelineSummaryDto getTimelineSummary(
      @PathVariable UUID id,
      @RequestParam(required = false) String granularity,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "false") boolean includePrivate) {
    guard.requireOwned(id);
    List<FieldProblem> problems = new ArrayList<>();
    Optional<TimelineGranularity> unit =
        granularity == null ? Optional.empty() : TimelineGranularity.fromCode(granularity);
    if (unit.isEmpty()) {
      problems.add(new FieldProblem("granularity", "INVALID_VALUE"));
    }
    if (from != null && to != null && from.isAfter(to)) {
      problems.add(new FieldProblem("from", "INVALID_TIME"));
    }
    if (!problems.isEmpty()) {
      throw new EntryRequestException(problems);
    }
    return mapper.toSummary(timeline.summary(id, unit.get(), from, to, includePrivate));
  }
}
