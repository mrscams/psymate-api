package com.psymate.agenda.controller;

import com.psymate.agenda.dto.AppointmentCreatedDto;
import com.psymate.agenda.dto.AppointmentDto;
import com.psymate.agenda.dto.AppointmentUpdatedDto;
import com.psymate.agenda.dto.CancelAppointmentRequest;
import com.psymate.agenda.dto.CreateAppointmentRequest;
import com.psymate.agenda.dto.UpdateAppointmentRequest;
import com.psymate.agenda.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
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
 * confirmer. Les lectures fenêtrées de l'agenda sont dans un autre contrôleur (M11-06).
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

  AppointmentController(AppointmentService appointments) {
    this.appointments = appointments;
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
          annulé ne compte pas. recurrence (répétition, à venir), status, practitionerId, \
          tenantId, sessionEntryId, authorType et submissionId sont refusés (UNKNOWN_FIELD).\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Rendez-vous créé ; en-tête Location = /api/appointments/{id}.",
        headers =
            @Header(
                name = "Location",
                description = "URL du rendez-vous créé, /api/appointments/{id}.",
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
            "PATIENT_ARCHIVED (errors[0].field = patientId) : patient archivé ; VALIDATION_FAILED"
                + " avec errors[] : patientId, startsAt, durationMin ou modality REQUIRED ;"
                + " durationMin OUT_OF_RANGE (hors 5 à 600) ; modality INVALID_VALUE (hors"
                + " in_person, video, phone) ; planningNote OUT_OF_RANGE (plus de 2 000"
                + " caractères) ; INVALID_FORMAT (date ou UUID illisible) ; UNKNOWN_FIELD (champ"
                + " refusé).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<AppointmentCreatedDto> createAppointment(
      @Valid @RequestBody CreateAppointmentRequest request) {
    AppointmentCreatedDto created = appointments.create(request);
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
}
