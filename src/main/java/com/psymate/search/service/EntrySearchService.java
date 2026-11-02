package com.psymate.search.service;

import com.psymate.search.dto.EntrySearchEmotionFacetDto;
import com.psymate.search.dto.EntrySearchFacetsDto;
import com.psymate.search.dto.EntrySearchItemDto;
import com.psymate.search.dto.EntrySearchQueryDto;
import com.psymate.search.dto.EntrySearchResultDto;
import com.psymate.search.dto.EntrySearchTagFacetDto;
import com.psymate.search.dto.EntrySearchTypeFacetDto;
import com.psymate.search.model.QuerySyntax;
import com.psymate.search.model.QuerySyntax.Parsed;
import com.psymate.search.model.SearchRequestException;
import com.psymate.search.model.SnippetBuilder;
import com.psymate.search.repository.EntrySearchRepository;
import com.psymate.search.repository.EntrySearchRepository.Filter;
import com.psymate.search.repository.EntrySearchRepository.Hit;
import com.psymate.shared.problem.FieldProblem;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recherche dans le dossier d'un patient : valide les paramètres, lit la syntaxe de q (tag:,
 * émotion:, avant:, après:), résout tags et émotions, puis assemble résultats exacts, résultats
 * approchés (repli trigramme) et facettes. L'appelant a déjà vérifié la propriété du patient.
 *
 * <p>Les valeurs d'une même dimension se combinent en « ou » (plusieurs tags : les entrées portant
 * l'un d'eux) ; les dimensions entre elles en « et ». Un tag ou une émotion inconnus ne retient
 * aucune entrée, sans erreur.
 */
@Service
public class EntrySearchService {

  static final int MAX_QUERY_LENGTH = 200;

  /** Plafond du numéro de page : borne l'OFFSET (10 000 résultats) et évite tout débordement. */
  static final int MAX_PAGE = 500;

  /** Bornes de période acceptées (au-delà, PostgreSQL refuserait l'instant). */
  private static final Instant MIN_BOUND = Instant.parse("0001-01-01T00:00:00Z");

  private static final Instant MAX_BOUND = Instant.parse("9999-12-31T23:59:59Z");

  /** Sous ce nombre de résultats exacts, la première page est complétée par des approchés. */
  static final int APPROXIMATE_BELOW = 3;

  /** Paramètres bruts de la requête HTTP. */
  public record Request(
      String q,
      List<String> types,
      List<String> emotions,
      List<String> tags,
      String from,
      String to,
      UUID sessionId,
      boolean includeArchived,
      int page) {}

  private final EntrySearchRepository repository;

  EntrySearchService(EntrySearchRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public EntrySearchResultDto search(UUID tenant, UUID me, UUID patient, Request r) {
    long started = System.nanoTime();
    List<FieldProblem> problems = new ArrayList<>();
    String q = r.q() == null ? "" : r.q();
    if (q.length() > MAX_QUERY_LENGTH) {
      problems.add(new FieldProblem("q", "TOO_LONG"));
    }
    if (r.page() < 1 || r.page() > MAX_PAGE) {
      problems.add(new FieldProblem("page", "INVALID_PAGE"));
    }
    Parsed parsed = QuerySyntax.parse(q.length() > MAX_QUERY_LENGTH ? "" : q);
    if (!parsed.errors().isEmpty()) {
      problems.add(new FieldProblem("q", "INVALID_DATE"));
    }
    Instant fromBound = bound("from", r.from(), false, problems);
    Instant toBound = bound("to", r.to(), true, problems);
    if (fromBound != null && toBound != null && !fromBound.isBefore(toBound)) {
      problems.add(new FieldProblem("to", "RANGE_INVERTED"));
    }
    List<String> types = distinct(r.types(), false);
    if (!types.isEmpty()) {
      List<String> known = repository.entryTypeCodes();
      if (!known.containsAll(types)) {
        problems.add(new FieldProblem("types", "UNKNOWN_TYPE"));
      }
    }
    if (!problems.isEmpty()) {
      throw new SearchRequestException(problems);
    }

    List<String> tagLabels = new ArrayList<>(parsed.tags());
    tagLabels.addAll(distinct(r.tags(), true));
    List<String> emotionValues = new ArrayList<>(parsed.emotions());
    emotionValues.addAll(distinct(r.emotions(), false));

    Instant lower = latest(fromBound, parsed.after());
    Instant upper = earliest(toBound, parsed.before());
    Filter filter =
        new Filter(
            tenant,
            patient,
            me,
            parsed.text(),
            types.isEmpty() ? null : types,
            tagLabels.isEmpty() ? null : repository.resolveTags(tenant, tagLabels),
            emotionValues.isEmpty() ? null : repository.resolveEmotions(tenant, emotionValues),
            lower,
            upper,
            r.sessionId(),
            r.includeArchived());

    long total = repository.countExact(filter);
    List<Hit> exact = total == 0 ? List.of() : repository.findExact(filter, r.page());
    List<Hit> approximate =
        filter.hasText() && !filter.negated() && total < APPROXIMATE_BELOW && r.page() == 1
            ? repository.findApproximate(filter)
            : List.of();

    List<Hit> all = new ArrayList<>(exact);
    all.addAll(approximate);
    Map<UUID, List<String>> tags = repository.tagLabels(all.stream().map(Hit::id).toList());
    List<EntrySearchItemDto> items = new ArrayList<>();
    for (Hit h : exact) {
      items.add(item(h, tags, false, filter));
    }
    for (Hit h : approximate) {
      items.add(item(h, tags, true, filter));
    }

    EntrySearchFacetsDto facets =
        new EntrySearchFacetsDto(
            repository.typeFacets(filter).stream()
                .map(c -> new EntrySearchTypeFacetDto(c.code(), c.count()))
                .toList(),
            repository.emotionFacets(filter).stream()
                .map(c -> new EntrySearchEmotionFacetDto(c.code(), c.label(), c.count()))
                .toList(),
            repository.tagFacets(filter).stream()
                .map(c -> new EntrySearchTagFacetDto(c.id(), c.label(), c.count()))
                .toList());
    EntrySearchQueryDto query =
        new EntrySearchQueryDto(
            parsed.text(), parsed.tags(), parsed.emotions(), parsed.before(), parsed.after());
    long tookMs = (System.nanoTime() - started) / 1_000_000;
    return new EntrySearchResultDto(
        query,
        r.page(),
        EntrySearchRepository.PAGE_SIZE,
        total,
        exact.size(),
        approximate.size(),
        items,
        facets,
        tookMs);
  }

  private static EntrySearchItemDto item(
      Hit h, Map<UUID, List<String>> tags, boolean approximate, Filter filter) {
    String title;
    String snippet;
    if (approximate) {
      title = h.title() == null ? null : SnippetBuilder.escape(h.title());
      snippet = SnippetBuilder.around(h.body(), filter.text());
    } else if (filter.hasText()) {
      title = SnippetBuilder.highlight(h.title(), h.markedTitle(), h.markedTitleFolded());
      snippet = SnippetBuilder.highlight(h.body(), h.markedSnippet(), h.markedSnippetFolded());
    } else {
      title = h.title() == null ? null : SnippetBuilder.escape(h.title());
      snippet = SnippetBuilder.plain(h.body());
    }
    return new EntrySearchItemDto(
        h.id(),
        h.type(),
        title,
        snippet,
        h.occurredFrom(),
        h.occurredTo(),
        h.occurredPrecision(),
        h.sessionId(),
        h.visibility(),
        h.archivedAt(),
        h.version(),
        tags.getOrDefault(h.id(), List.of()),
        h.emotionCode(),
        h.rank(),
        h.similarity(),
        approximate);
  }

  /** Valeurs sans doublon, sans blancs ; pour un tag, sans le « # » de tête. */
  private static List<String> distinct(List<String> values, boolean tag) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (values != null) {
      for (String v : values) {
        String s = v == null ? "" : v.strip();
        if (tag && s.startsWith("#")) {
          s = s.substring(1).strip();
        }
        if (!s.isEmpty()) {
          out.add(s);
        }
      }
    }
    return List.copyOf(out);
  }

  /**
   * Borne de période : date AAAA-MM-JJ ou instant ISO 8601. {@code from} commence au début du jour
   * ; {@code to} est ramené à une borne exclusive (jour suivant pour une date, +1 µs pour un
   * instant, donc inclusif).
   */
  private static Instant bound(
      String field, String value, boolean upper, List<FieldProblem> problems) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String v = value.strip();
    try {
      Instant i;
      if (v.length() == 10) {
        LocalDate d = LocalDate.parse(v);
        i = (upper ? d.plusDays(1) : d).atStartOfDay().toInstant(ZoneOffset.UTC);
      } else {
        i = OffsetDateTime.parse(v).toInstant().truncatedTo(ChronoUnit.MICROS);
        i = upper ? i.plus(1, ChronoUnit.MICROS) : i;
      }
      if (i.isBefore(MIN_BOUND) || i.isAfter(MAX_BOUND)) {
        problems.add(new FieldProblem(field, "INVALID_DATE"));
        return null;
      }
      return i;
    } catch (DateTimeException e) {
      problems.add(new FieldProblem(field, "INVALID_DATE"));
      return null;
    }
  }

  private static Instant latest(Instant a, Instant b) {
    return a == null ? b : b == null ? a : a.isAfter(b) ? a : b;
  }

  private static Instant earliest(Instant a, Instant b) {
    return a == null ? b : b == null ? a : a.isBefore(b) ? a : b;
  }
}
