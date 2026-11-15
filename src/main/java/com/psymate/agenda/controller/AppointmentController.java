package com.psymate.agenda.controller;

import com.psymate.agenda.dto.AppointmentCreatedDto;
import com.psymate.agenda.dto.AppointmentDto;
import com.psymate.agenda.dto.AppointmentSeriesDto;
import com.psymate.agenda.dto.AppointmentUpdatedDto;
import com.psymate.agenda.dto.CancelAppointmentRequest;
import com.psymate.agenda.dto.CreateAppointmentRequest;
import com.psymate.agenda.dto.RecurrenceRequest;
import com.psymate.agenda.dto.UpdateAppointmentRequest;
import com.psymate.agenda.service.AppointmentService;
import com.psymate.agenda.service.RecurrenceCalculator;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rendez-vous (M11-05, fiches 15 et 15b) : créer depuis un créneau, lire, déplacer, annuler,
 * confirmer ; séries (M11-07) : création répétée et annulation de série. Les lectures fenêtrées de
 * l'agenda sont dans un autre contrôleur (M11-06).
 */
@Tag(
    name = "Rendez-vous",
    description =
        "Rendez-vous de l'agenda du praticien : création depuis un créneau, détail, déplacement,"
            + " annulation et confirmation. Un chevauchement n'est jamais bloquant : il est"
            + " signalé dans conflicts. Un rendez-vous ne se supprime jamais.")
@RestController
class AppointmentController {

  private static final String PROBLEM = "application/problem+json";
  private static final String ID_EXAMPLE = "5d2f6a1e-8b3c-4d7e-9f10-2a3b4c5d6e7f";
  private static final String NOT_FOUND =
      "APPOINTMENT_NOT_FOUND : rendez-vous inexistant ou d'un autre praticien.";
  private static final String MALFORMED =
      "MALFORMED_REQUEST : identifiant qui n'est pas un UUID, ou corps JSON illisible.";

  private final AppointmentService appointments;
  private final AgendaMessages messages;

  AppointmentController(AppointmentService appointments, AgendaMessages messages) {
    this.appointments = appointments;
    this.messages = messages;
  }

  // ---- validation croisée de la création (M11-07)

  /**
   * Fuseau du calcul des occurrences (défaut Europe/Paris) et règles de recurrence que Jakarta
   * Validation n'exprime pas : everyWeeks parmi 1, 2, 4 ; exactement un de count et until (REQUIRED
   * si aucun, INVALID_VALUE si les deux, sur le champ recurrence) ; until pas avant le jour du
   * premier rendez-vous. 422 VALIDATION_FAILED avec toutes les erreurs ; le dépassement de 26
   * occurrences est ensuite vérifié par le service (TOO_MANY_OCCURRENCES).
   */
  private ZoneId validated(CreateAppointmentRequest request, HttpServletRequest http) {
    List<FieldProblem> problems = new ArrayList<>();
    ZoneId zone = RecurrenceCalculator.DEFAULT_ZONE;
    String tz = request.tz();
    if (tz != null && !tz.isBlank()) {
      try {
        zone = ZoneId.of(tz.strip());
      } catch (DateTimeException e) {
        problems.add(new FieldProblem("tz", "INVALID_VALUE"));
      }
    }
    RecurrenceRequest recurrence = request.recurrence();
    if (recurrence != null) {
      if (!RecurrenceCalculator.EVERY_WEEKS.contains(recurrence.everyWeeks())) {
        problems.add(new FieldProblem("recurrence.everyWeeks", "INVALID_VALUE"));
      }
      if (recurrence.count() == null && recurrence.until() == null) {
        problems.add(new FieldProblem("recurrence", "REQUIRED"));
      } else if (recurrence.count() != null && recurrence.until() != null) {
        problems.add(new FieldProblem("recurrence", "INVALID_VALUE"));
      } else if (recurrence.until() != null
          && problems.isEmpty()
          && recurrence.until().isBefore(request.startsAt().atZone(zone).toLocalDate())) {
        problems.add(new FieldProblem("recurrence.until", "INVALID_VALUE"));
      }
    }
    if (!problems.isEmpty()) {
      Locale locale = messages.localeOf(http);
      throw new ProblemException(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "VALIDATION_FAILED",
          messages.get("agenda.error.validation.title", locale),
          messages.get("agenda.error.validation.detail", locale),
          problems.stream()
              .map(p -> new FieldProblem(p.field(), p.code(), messages.message(p, locale)))
              .toList());
    }
    return zone;
  }

  @PostMapping("/api/appointments")
  @Operation(
      summary = "Crée un rendez-vous depuis un créneau",
      description =
          """
          Session praticien, propriétaire du patient. Le praticien et le cabinet viennent de la \
          session. La fin vaut startsAt + durationMin. Statut planned, ou tentative si \
          tentative = true (« À confirmer »). Les rendez-vous actifs du praticien qui chevauchent \
          le créneau sont renvoyés dans conflicts sans empêcher la création ; un rendez-vous \
          annulé ne compte pas. Avec recurrence (G Q55), une ligne par occurrence est créée dans \
          la même transaction, toutes avec le même seriesId, le même statut et la même note, \
          toutes les everyWeeks semaines à la même heure locale dans le fuseau tz (défaut \
          Europe/Paris), count fois ou jusqu'à until inclus ; au-delà de 26 occurrences, rien \
          n'est créé (422 TOO_MANY_OCCURRENCES). conflicts agrège alors les chevauchements de \
          chaque occurrence, occurrenceStartsAt indiquant laquelle. Chaque occurrence se déplace \
          et s'annule ensuite seule ; aucune règle de répétition n'est stockée. status, \
          practitionerId, tenantId, sessionEntryId, authorType et submissionId sont refusés \
          (UNKNOWN_FIELD).\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description =
            "Rendez-vous créé, ou occurrences de la série créées ; en-tête Location ="
                + " /api/appointments/{id} de la première occurrence.",
        headers =
            @Header(
                name = "Location",
                description =
                    "URL du rendez-vous créé (première occurrence d'une série),"
                        + " /api/appointments/{id}.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : corps JSON absent ou illisible.",
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
            "PATIENT_ARCHIVED (errors[0].field = patientId) : patient archivé ;"
                + " TOO_MANY_OCCURRENCES (errors[0].field = recurrence) : plus de 26 occurrences,"
                + " rien n'est créé ; VALIDATION_FAILED avec errors[] : patientId, startsAt,"
                + " durationMin, modality ou recurrence.everyWeeks REQUIRED ; durationMin"
                + " OUT_OF_RANGE (hors 5 à 600) ; modality INVALID_VALUE (hors in_person, video,"
                + " phone) ; planningNote OUT_OF_RANGE (plus de 2 000 caractères) ;"
                + " recurrence.everyWeeks INVALID_VALUE (hors 1, 2, 4) ; recurrence.count"
                + " OUT_OF_RANGE (moins de 1) ; recurrence REQUIRED (ni count ni until) ou"
                + " INVALID_VALUE (count et until ensemble) ; recurrence.until INVALID_VALUE (avant"
                + " le premier rendez-vous) ; tz INVALID_VALUE (fuseau inconnu) ; INVALID_FORMAT"
                + " (date ou UUID illisible) ; UNKNOWN_FIELD (champ refusé).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<AppointmentCreatedDto> createAppointment(
      @Valid @RequestBody CreateAppointmentRequest request, HttpServletRequest http) {
    AppointmentCreatedDto created = appointments.create(request, validated(request, http));
    UUID id = created.appointments().getFirst().id();
    return ResponseEntity.created(URI.create("/api/appointments/" + id)).body(created);
  }

  @GetMapping("/api/appointments/{id}")
  @Operation(
      summary = "Détail d'un rendez-vous",
      description =
          """
          Session praticien. Rendez-vous du praticien courant, avec son patient (nom affiché, date \
          de naissance, archivé). Le rendez-vous d'un autre praticien répond 404.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Rendez-vous."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  AppointmentDto getAppointment(
      @Parameter(description = "Identifiant du rendez-vous.", example = ID_EXAMPLE) @PathVariable
          UUID id) {
    return appointments.get(id);
  }

  @PatchMapping("/api/appointments/{id}")
  @Operation(
      summary = "Déplace ou modifie un rendez-vous",
      description =
          """
          Session praticien. Fusion partielle : startsAt déplace le rendez-vous en gardant sa \
          durée, durationMin la change en gardant le début, modality et planningNote se \
          remplacent ; un champ absent reste inchangé, planningNote à null efface la note. Refusé \
          si le rendez-vous est annulé ou terminé (409). Les chevauchements du nouveau créneau \
          sont renvoyés dans conflicts sans empêcher la modification. patientId, tentative \
          (passer par /confirm), recurrence, status, practitionerId, tenantId, sessionEntryId, \
          authorType et submissionId sont refusés (UNKNOWN_FIELD).\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Rendez-vous modifié et chevauchements."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "APPOINTMENT_NOT_ACTIVE : le rendez-vous est annulé ou terminé.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : durationMin OUT_OF_RANGE (hors 5 à 600) ; modality"
                + " INVALID_VALUE ; planningNote OUT_OF_RANGE (plus de 2 000 caractères) ;"
                + " INVALID_FORMAT (date illisible) ; UNKNOWN_FIELD (champ refusé).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  AppointmentUpdatedDto updateAppointment(
      @Parameter(description = "Identifiant du rendez-vous.", example = ID_EXAMPLE) @PathVariable
          UUID id,
      @Valid @RequestBody UpdateAppointmentRequest request) {
    return appointments.update(id, request);
  }

  @PostMapping("/api/appointments/{id}/cancel")
  @Operation(
      summary = "Annule un rendez-vous",
      description =
          """
          Session praticien. Le rendez-vous passe à cancelled avec la date d'annulation et le \
          motif facultatif ; il n'est jamais supprimé. Idempotent : annuler un rendez-vous déjà \
          annulé répond 200 sans rien changer (motif et date d'origine gardés). Refusé si le \
          rendez-vous est terminé (409). Corps facultatif.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Rendez-vous annulé."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "APPOINTMENT_NOT_ACTIVE : le rendez-vous est terminé (séance rattachée).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : reason OUT_OF_RANGE (plus de 500 caractères) ;"
                + " UNKNOWN_FIELD (champ refusé).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  AppointmentDto cancelAppointment(
      @Parameter(description = "Identifiant du rendez-vous.", example = ID_EXAMPLE) @PathVariable
          UUID id,
      @Valid @RequestBody(required = false) CancelAppointmentRequest request) {
    return appointments.cancel(id, request == null ? null : request.reason());
  }

  @PostMapping("/api/appointments/{id}/confirm")
  @Operation(
      summary = "Confirme un rendez-vous à confirmer",
      description =
          """
          Session praticien. Fait passer un rendez-vous tentative (« À confirmer ») à planned. \
          Tout autre statut répond 409. Aucun corps.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Rendez-vous confirmé."),
    @ApiResponse(
        responseCode = "400",
        description = MALFORMED,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = NOT_FOUND,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "APPOINTMENT_NOT_ACTIVE : le rendez-vous n'est pas à confirmer (planned, annulé ou"
                + " terminé).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  AppointmentDto confirmAppointment(
      @Parameter(description = "Identifiant du rendez-vous.", example = ID_EXAMPLE) @PathVariable
          UUID id) {
    return appointments.confirm(id);
  }

  @PostMapping("/api/appointments/series/{seriesId}/cancel")
  @Operation(
      summary = "Annule une série de rendez-vous",
      description =
          """
          Session praticien. Annule toutes les occurrences encore actives (planned, tentative) \
          de la série du praticien courant, déplacées ou non, avec le même motif facultatif ; \
          les occurrences déjà annulées ou terminées restent telles quelles. Rien n'est \
          supprimé. C'est l'« Annuler » du message affiché après la création d'une série. \
          Répond toutes les occurrences de la série dans leur état final. Une série d'un autre \
          praticien répond 404. Corps facultatif.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Occurrences de la série, état final."),
    @ApiResponse(
        responseCode = "400",
        description =
            "MALFORMED_REQUEST : identifiant qui n'est pas un UUID, ou corps JSON illisible.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "SERIES_NOT_FOUND : aucune occurrence de cette série n'est au praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : reason OUT_OF_RANGE (plus de 500 caractères) ;"
                + " UNKNOWN_FIELD (champ refusé).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  AppointmentSeriesDto cancelSeries(
      @Parameter(description = "Identifiant de la série (seriesId).", example = ID_EXAMPLE)
          @PathVariable
          UUID seriesId,
      @Valid @RequestBody(required = false) CancelAppointmentRequest request) {
    return appointments.cancelSeries(seriesId, request == null ? null : request.reason());
  }
}
