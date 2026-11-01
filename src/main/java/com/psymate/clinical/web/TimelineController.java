package com.psymate.clinical.web;

import com.psymate.clinical.application.TimelineCursor;
import com.psymate.clinical.application.TimelineGranularity;
import com.psymate.clinical.application.TimelineQuery;
import com.psymate.clinical.application.TimelineQueryService;
import com.psymate.clinical.domain.EntryType;
import com.psymate.clinical.web.TimelineDtos.TimelinePageDto;
import com.psymate.clinical.web.TimelineDtos.TimelineSummaryDto;
import com.psymate.patients.api.PatientOwnershipGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Timeline d'un patient (A6) : fenêtre triée par occurred_from décroissant avec curseur keyset,
 * facettes de la fenêtre, situations qui la chevauchent ; résumé par mois ou par année.
 */
@Tag(
    name = "Chronologie",
    description =
        "Chronologie d'un patient : entrées triées de la plus récente à la plus ancienne avec"
            + " pagination par curseur, facettes, situations en cours, et résumé par mois ou par"
            + " année.")
@RestController
class TimelineController {

  static final int MAX_LIMIT = 200;

  private static final String PROBLEM = "application/problem+json";
  private static final String PATIENT_ID_EXAMPLE = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e";

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
  @Operation(
      summary = "Liste la chronologie du patient par fenêtre de dates et curseur",
      description =
          """
          Session praticien, propriétaire du patient. Entrées non archivées dont le début \
          d'occurrence est dans [from, to[, de la plus récente à la plus ancienne. Brouillons \
          inclus (draftsOnly=true pour eux seuls) ; entrées privées exclues sauf \
          includePrivate=true. Dans une même liste de filtres (types, emotions, tags), un élément \
          doit correspondre à l'une des valeurs ; les trois filtres se cumulent. Les facettes \
          portent sur toute la fenêtre filtrée et ongoingSituations liste les situations en cours \
          qui chevauchent la fenêtre. Pagination par curseur opaque : renvoyer nextCursor dans \
          cursor, nextCursor null sur la dernière page. limit : 1 à 200, 50 par défaut.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Page de chronologie avec facettes."),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : identifiant du patient qui n'est pas un UUID.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "PATIENT_NOT_FOUND : patient inexistant.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message}, une erreur par paramètre"
                + " fautif : limit OUT_OF_RANGE (hors 1 à 200) ; from INVALID_TIME (from après to)"
                + " ; types INVALID_VALUE (type inconnu) ; cursor INVALID_VALUE (curseur illisible)"
                + " ; from, to, limit, draftsOnly, includePrivate INVALID_FORMAT (valeur"
                + " illisible).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  TimelinePageDto getTimeline(
      @Parameter(description = "Identifiant du patient.", example = PATIENT_ID_EXAMPLE)
          @PathVariable
          UUID id,
      @Parameter(
              description =
                  "Début de la fenêtre, inclus, sur le début d'occurrence : date-heure ISO 8601"
                      + " UTC. Absent : sans borne.",
              example = "2026-01-01T00:00:00Z")
          @RequestParam(required = false)
          Instant from,
      @Parameter(
              description =
                  "Fin de la fenêtre, exclue, sur le début d'occurrence : date-heure ISO 8601 UTC."
                      + " Ne doit pas précéder from. Absent : sans borne.",
              example = "2026-11-01T00:00:00Z")
          @RequestParam(required = false)
          Instant to,
      @Parameter(
              description =
                  "Types à garder : note, event, situation, feeling, session, medication. Valeurs"
                      + " séparées par des virgules ou paramètre répété. Absent : tous les types.",
              example = "feeling,event")
          @RequestParam(required = false)
          List<String> types,
      @Parameter(
              description =
                  "Codes d'émotion à garder (ressentis seulement). Valeurs séparées par des"
                      + " virgules ou paramètre répété. Absent : sans filtre.",
              example = "anxiete,tristesse")
          @RequestParam(required = false)
          List<String> emotions,
      @Parameter(
              description =
                  "Libellés de tags à garder. Valeurs séparées par des virgules ou paramètre"
                      + " répété. Un libellé inconnu ne correspond à rien. Absent : sans filtre.",
              example = "travail,famille")
          @RequestParam(required = false)
          List<String> tags,
      @Parameter(
              description =
                  "Vrai pour ne garder que les brouillons. Défaut : faux (brouillons inclus).",
              example = "false")
          @RequestParam(defaultValue = "false")
          boolean draftsOnly,
      @Parameter(
              description = "Vrai pour inclure les entrées privées. Défaut : faux (exclues).",
              example = "false")
          @RequestParam(defaultValue = "false")
          boolean includePrivate,
      @Parameter(
              description =
                  "Curseur opaque de la page suivante, tel que renvoyé dans nextCursor. Absent :"
                      + " première page.")
          @RequestParam(required = false)
          String cursor,
      @Parameter(
              description = "Nombre maximal d'éléments par page, de 1 à 200. Défaut : 50.",
              example = "50")
          @RequestParam(defaultValue = "50")
          int limit) {
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
  @Operation(
      summary = "Résume la chronologie du patient par mois ou par année",
      description =
          """
          Session praticien, propriétaire du patient. Pour chaque période UTC non vide : nombre \
          d'entrées par type, nombre de ressentis et intensité moyenne. Entrées non archivées dont \
          le début d'occurrence est dans [from, to[, brouillons inclus, entrées privées exclues \
          sauf includePrivate=true. Calculé à la volée, sans pagination, de la période la plus \
          ancienne à la plus récente.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Résumé par période."),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : identifiant du patient qui n'est pas un UUID.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "PATIENT_NOT_FOUND : patient inexistant.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} : granularity INVALID_VALUE"
                + " (absente, ou hors month et year) ; from INVALID_TIME (from après to) ; from,"
                + " to, includePrivate INVALID_FORMAT (valeur illisible).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  TimelineSummaryDto getTimelineSummary(
      @Parameter(description = "Identifiant du patient.", example = PATIENT_ID_EXAMPLE)
          @PathVariable
          UUID id,
      @Parameter(
              description =
                  "Granularité du résumé : month (un groupe par mois) ou year (par année)."
                      + " Obligatoire, en minuscules.",
              schema = @Schema(allowableValues = {"month", "year"}),
              example = "month")
          @RequestParam(required = false)
          String granularity,
      @Parameter(
              description =
                  "Début de la fenêtre, inclus, sur le début d'occurrence : date-heure ISO 8601"
                      + " UTC. Absent : sans borne.",
              example = "2026-01-01T00:00:00Z")
          @RequestParam(required = false)
          Instant from,
      @Parameter(
              description =
                  "Fin de la fenêtre, exclue, sur le début d'occurrence : date-heure ISO 8601 UTC."
                      + " Ne doit pas précéder from. Absent : sans borne.",
              example = "2026-11-01T00:00:00Z")
          @RequestParam(required = false)
          Instant to,
      @Parameter(
              description = "Vrai pour inclure les entrées privées. Défaut : faux (exclues).",
              example = "false")
          @RequestParam(defaultValue = "false")
          boolean includePrivate) {
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
