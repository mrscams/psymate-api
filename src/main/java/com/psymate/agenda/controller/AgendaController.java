package com.psymate.agenda.controller;

import com.psymate.agenda.dto.AgendaWindowDto;
import com.psymate.agenda.dto.BookingRequestsDto;
import com.psymate.agenda.dto.FreeBusyDto;
import com.psymate.agenda.dto.PatientAppointmentsDto;
import com.psymate.agenda.model.AppointmentException;
import com.psymate.agenda.service.AgendaQueryService;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lectures de l'agenda (M11-06, I3) : fenêtre de la grille et de la liste, voisinage d'un créneau
 * (freebusy), rendez-vous d'un patient avec ses défauts de création, demandes en ligne en attente
 * (M14-04). Les bornes sont validées ici (422 VALIDATION_FAILED avec errors[] par paramètre, 422
 * WINDOW_TOO_LARGE au-delà de 62 jours) ; les lectures sont celles d'{@link AgendaQueryService}.
 */
@Tag(
    name = "Agenda",
    description =
        "Agenda du praticien : rendez-vous planifiés, fenêtre de la grille, conflits d'un créneau"
            + " et rendez-vous d'un patient. Dates en UTC ISO 8601 ; le client convertit dans le"
            + " fuseau du praticien. Aucune synchronisation externe dans cette version.")
@RestController
class AgendaController {

  static final int MAX_LIMIT = 100;
  static final String DEFAULT_TZ = "Europe/Paris";

  private static final String PROBLEM = "application/problem+json";
  private static final String PATIENT_ID_EXAMPLE = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e";

  private final AgendaQueryService agenda;
  private final AgendaMessages messages;

  AgendaController(AgendaQueryService agenda, AgendaMessages messages) {
    this.agenda = agenda;
    this.messages = messages;
  }

  // ---- validation des paramètres

  /**
   * Erreurs de paramètres : 422 VALIDATION_FAILED, messages de l'agenda dans la langue demandée.
   */
  private ProblemException invalid(List<FieldProblem> problems, HttpServletRequest request) {
    Locale locale = messages.localeOf(request);
    List<FieldProblem> errors =
        problems.stream()
            .map(p -> new FieldProblem(p.field(), p.code(), messages.message(p, locale)))
            .toList();
    return new ProblemException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "VALIDATION_FAILED",
        messages.get("agenda.error.validation.title", locale),
        messages.get("agenda.error.validation.detail", locale),
        errors);
  }

  /**
   * from et to obligatoires, from strictement avant to, au plus 62 jours : erreurs de champ
   * d'abord, WINDOW_TOO_LARGE ensuite.
   */
  private void requireWindow(
      Instant from, Instant to, List<FieldProblem> problems, HttpServletRequest request) {
    if (from == null) {
      problems.add(new FieldProblem("from", "REQUIRED"));
    }
    if (to == null) {
      problems.add(new FieldProblem("to", "REQUIRED"));
    }
    if (from != null && to != null && !from.isBefore(to)) {
      problems.add(new FieldProblem("to", "INVALID_VALUE"));
    }
    if (!problems.isEmpty()) {
      throw invalid(problems, request);
    }
    if (Duration.between(from, to).compareTo(Duration.ofDays(AppointmentException.MAX_WINDOW_DAYS))
        > 0) {
      throw AppointmentException.windowTooLarge();
    }
  }

  // ---- routes

  @GetMapping("/api/agenda")
  @Operation(
      summary = "Liste les rendez-vous du praticien sur une fenêtre de dates",
      description =
          """
          Session praticien. Rendez-vous du praticien courant dont le créneau chevauche la \
          fenêtre [from, to[ (startsAt < to et endsAt > from : un rendez-vous à cheval sur une \
          borne est inclus), triés par début. Les rendez-vous annulés sont exclus sauf \
          includeCancelled=true. Fenêtre de 62 jours au plus, assez pour une grille mensuelle \
          avec débords. busy est réservé aux indisponibilités externes et toujours vide dans \
          cette version.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Rendez-vous de la fenêtre."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} : from ou to REQUIRED"
                + " (absent) ; to INVALID_VALUE (to ne suit pas from) ; from, to,"
                + " includeCancelled INVALID_FORMAT (valeur illisible). WINDOW_TOO_LARGE avec"
                + " errors[0].field = to : la fenêtre dépasse 62 jours.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  AgendaWindowDto window(
      @Parameter(
              description = "Début de la fenêtre, inclus : date-heure ISO 8601 UTC. Obligatoire.",
              example = "2026-11-16T00:00:00Z")
          @RequestParam(required = false)
          Instant from,
      @Parameter(
              description =
                  "Fin de la fenêtre, exclue : date-heure ISO 8601 UTC, strictement après from et"
                      + " au plus 62 jours après. Obligatoire.",
              example = "2026-11-23T00:00:00Z")
          @RequestParam(required = false)
          Instant to,
      @Parameter(
              description = "Vrai pour inclure les rendez-vous annulés. Défaut : faux.",
              example = "false")
          @RequestParam(defaultValue = "false")
          boolean includeCancelled,
      HttpServletRequest request) {
    requireWindow(from, to, new ArrayList<>(), request);
    return agenda.window(from, to, includeCancelled);
  }

  @GetMapping("/api/agenda/freebusy")
  @Operation(
      summary = "Indique les conflits et le voisinage d'un créneau",
      description =
          """
          Session praticien. Avant de créer un rendez-vous ou d'en déplacer un : rendez-vous \
          actifs (planned, tentative) du praticien courant qui chevauchent [from, to[ \
          (conflicts, jamais bloquants), dernier rendez-vous actif terminé au plus tard à from \
          (previous) et premier commençant au plus tôt à to (next), tous deux le même jour \
          calendaire que from dans le fuseau tz. excludeAppointmentId retire le rendez-vous \
          déplacé de ses propres conflits et de son voisinage. Les annulés ne comptent jamais.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Conflits et voisinage du créneau."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} : from ou to REQUIRED"
                + " (absent) ; to INVALID_VALUE (to ne suit pas from) ; tz INVALID_VALUE (fuseau"
                + " inconnu) ; from, to, excludeAppointmentId INVALID_FORMAT (valeur illisible)."
                + " WINDOW_TOO_LARGE avec errors[0].field = to : le créneau dépasse 62 jours.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  FreeBusyDto freeBusy(
      @Parameter(
              description = "Début du créneau, inclus : date-heure ISO 8601 UTC. Obligatoire.",
              example = "2026-11-16T10:00:00Z")
          @RequestParam(required = false)
          Instant from,
      @Parameter(
              description =
                  "Fin du créneau, exclue : date-heure ISO 8601 UTC, strictement après from."
                      + " Obligatoire.",
              example = "2026-11-16T10:50:00Z")
          @RequestParam(required = false)
          Instant to,
      @Parameter(
              description =
                  "Rendez-vous à ignorer : celui qu'on déplace. Absent : aucun n'est ignoré.")
          @RequestParam(required = false)
          UUID excludeAppointmentId,
      @Parameter(
              description =
                  "Fuseau IANA qui définit le jour calendaire de previous et next. Défaut :"
                      + " Europe/Paris.",
              example = "Europe/Paris")
          @RequestParam(required = false)
          String tz,
      HttpServletRequest request) {
    List<FieldProblem> problems = new ArrayList<>();
    ZoneId zone = null;
    try {
      zone = ZoneId.of(tz == null || tz.isBlank() ? DEFAULT_TZ : tz.strip());
    } catch (DateTimeException e) {
      problems.add(new FieldProblem("tz", "INVALID_VALUE"));
    }
    requireWindow(from, to, problems, request);
    return agenda.freeBusy(from, to, excludeAppointmentId, zone);
  }

  @GetMapping("/api/agenda/requests")
  @Operation(
      summary = "Liste les demandes de rendez-vous en ligne en attente",
      description =
          """
          Session praticien. Demandes en ligne qui attendent une réponse (G Q74) : rendez-vous \
          du praticien courant d'origine online au statut tentative qui commencent au plus tôt \
          maintenant, triés par début puis identifiant, 100 au plus. Un rendez-vous à confirmer \
          posé par le praticien (origin practitioner), une demande déjà confirmée (planned), \
          annulée ou passée n'y figurent pas. Le praticien confirme une demande par \
          POST /api/appointments/{id}/confirm et la refuse par POST /api/appointments/{id}/cancel.\
          """)
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Demandes en attente, à venir.")})
  BookingRequestsDto pendingRequests() {
    return agenda.pendingRequests();
  }

  @GetMapping("/api/patients/{id}/appointments")
  @Operation(
      summary = "Liste les rendez-vous à venir d'un patient et ses défauts de création",
      description =
          """
          Session praticien, propriétaire du patient. Rendez-vous actifs (planned, tentative) du \
          praticien courant pour ce patient qui ne sont pas terminés à l'instant from (défaut : \
          maintenant), triés par début, au plus limit (1 à 100, 20 par défaut). defaults donne \
          la durée et la modalité à préremplir (suivi du patient, sinon préférence \
          sessions.defaultDurationMin, sinon 60 minutes au cabinet) et le rythme du suivi, \
          affiché tel quel.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Rendez-vous du patient et défauts."),
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
            "VALIDATION_FAILED avec errors[] {field, code, message} : limit OUT_OF_RANGE (hors 1"
                + " à 100) ; from, limit INVALID_FORMAT (valeur illisible).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  PatientAppointmentsDto ofPatient(
      @Parameter(description = "Identifiant du patient.", example = PATIENT_ID_EXAMPLE)
          @PathVariable
          UUID id,
      @Parameter(
              description =
                  "Instant de référence : date-heure ISO 8601 UTC ; seuls les rendez-vous qui se"
                      + " terminent après lui sont renvoyés. Absent : maintenant.",
              example = "2026-11-16T00:00:00Z")
          @RequestParam(required = false)
          Instant from,
      @Parameter(
              description = "Nombre maximal de rendez-vous, de 1 à 100. Défaut : 20.",
              schema =
                  @Schema(
                      type = "integer",
                      format = "int32",
                      minimum = "1",
                      maximum = "100",
                      defaultValue = "20"),
              example = "20")
          @RequestParam(defaultValue = "20")
          int limit,
      HttpServletRequest request) {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw invalid(List.of(new FieldProblem("limit", "OUT_OF_RANGE")), request);
    }
    return agenda.ofPatient(id, from, limit);
  }
}
