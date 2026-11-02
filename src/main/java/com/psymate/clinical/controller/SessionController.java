package com.psymate.clinical.controller;

import com.psymate.clinical.api.SessionView;
import com.psymate.clinical.dto.CloseSessionRequest;
import com.psymate.clinical.dto.EntryDto;
import com.psymate.clinical.dto.EntryMapper;
import com.psymate.clinical.dto.RecordContactRequest;
import com.psymate.clinical.dto.StartSessionRequest;
import com.psymate.clinical.model.ClinicalError;
import com.psymate.clinical.model.ClinicalException;
import com.psymate.clinical.service.CloseSessionCommand;
import com.psymate.clinical.service.ContactCommand;
import com.psymate.clinical.service.EntryQueryService;
import com.psymate.clinical.service.SessionService;
import com.psymate.clinical.service.StartSessionCommand;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Séances (UC-04, F11) : démarrer, clôturer, consigner un contact, séance courante. */
@Tag(
    name = "Séances",
    description =
        "Séances d'un patient : démarrage, clôture avec compte rendu, contact hors rendez-vous et"
            + " séance courante. Une séance est une entrée de type session ; toute autre entrée"
            + " appartient à une séance.")
@RestController
class SessionController {

  private static final String PROBLEM = "application/problem+json";
  private static final String PATIENT_ID_EXAMPLE = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e";

  private final SessionService sessions;
  private final EntryQueryService queries;
  private final EntryMapper mapper;

  SessionController(SessionService sessions, EntryQueryService queries, EntryMapper mapper) {
    this.sessions = sessions;
    this.queries = queries;
    this.mapper = mapper;
  }

  /** Démarre une séance maintenant ; 409 si une séance est déjà ouverte. */
  @PostMapping("/api/patients/{id}/sessions/start")
  @Operation(
      summary = "Démarre une séance maintenant pour le patient",
      description =
          """
          Session praticien, propriétaire du patient. Une seule séance peut être ouverte par patient \
          (la séance d'accueil créée avec le patient ne compte pas). La séance démarre à l'instant \
          présent, durée inconnue jusqu'à la clôture (POST /api/sessions/{id}/close). Réponse 201 \
          avec Location. authorType et submissionId sont refusés (UNKNOWN_FIELD).\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Séance ouverte créée ; en-tête Location = /api/entries/{id} de la séance.",
        headers =
            @Header(
                name = "Location",
                description = "URL de la séance créée, /api/entries/{id}.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description =
            "MALFORMED_REQUEST : identifiant du patient qui n'est pas un UUID, ou corps JSON"
                + " absent ou illisible.",
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
        responseCode = "409",
        description = "SESSION_ALREADY_OPEN : une séance est déjà ouverte pour ce patient.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : modality REQUIRED (absente) ou INVALID_VALUE (vide"
                + " ou hors in_person, video, phone, email) ; UNKNOWN_FIELD si authorType ou"
                + " submissionId est envoyé.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<EntryDto> startSession(
      @Parameter(description = "Identifiant du patient.", example = PATIENT_ID_EXAMPLE)
          @PathVariable
          UUID id,
      @Valid @RequestBody StartSessionRequest request) {
    UUID sessionId = sessions.start(id, new StartSessionCommand(request.modality(), null));
    return created(sessionId);
  }

  /** Clôture la séance : durée, fin et compte rendu figés dans une version. */
  @PostMapping("/api/sessions/{id}/close")
  @Operation(
      summary = "Clôture une séance ouverte en figeant durée, fin et compte rendu",
      description =
          """
          Session praticien, propriétaire du patient de la séance. La durée (sinon la durée par \
          défaut du praticien, 60 minutes à défaut), l'heure de fin et le compte rendu (summary) \
          sont figés dans une nouvelle version, même dans la fenêtre de grâce de 1 h 15. Une séance \
          ne se clôture qu'une fois. authorType et submissionId sont refusés (UNKNOWN_FIELD). Le \
          corps est obligatoire, un objet vide convient.\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Séance clôturée : nouvelle version portant durée, fin et compte rendu."),
    @ApiResponse(
        responseCode = "400",
        description =
            "MALFORMED_REQUEST : identifiant de la séance qui n'est pas un UUID, ou corps JSON"
                + " absent ou illisible.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : la séance appartient au dossier d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ENTRY_NOT_FOUND : séance inexistante.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "SESSION_ALREADY_CLOSED : la séance est déjà clôturée ; ENTRY_ARCHIVED : la séance"
                + " est archivée.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "SESSION_INVALID : l'entrée désignée n'est pas une séance (code racine, errors[0].field"
                + " = sessionId) ; VALIDATION_FAILED avec errors[] : durationMin OUT_OF_RANGE (hors"
                + " 1 à 600) ou INVALID_FORMAT, UNKNOWN_FIELD si authorType ou submissionId est"
                + " envoyé.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  EntryDto closeSession(
      @Parameter(
              description = "Identifiant de la séance (entrée de type session).",
              example = "3f8e2b1a-6c4d-4e57-9a0b-1d2c3e4f5a6b")
          @PathVariable
          UUID id,
      @Valid @RequestBody CloseSessionRequest request) {
    sessions.close(id, new CloseSessionCommand(request.durationMin(), request.summary()));
    return mapper.toDto(queries.get(id));
  }

  /** Contact hors rendez-vous (téléphone, e-mail), consigné comme une séance déjà clôturée. */
  @PostMapping("/api/patients/{id}/sessions/contact")
  @Operation(
      summary = "Consigne un contact hors rendez-vous comme une séance déjà clôturée",
      description =
          """
          Session praticien, propriétaire du patient. Crée une séance de genre contact (téléphone ou \
          e-mail), clôturée d'emblée, à la date indiquée, dont le texte devient le compte rendu. \
          N'affecte pas la séance éventuellement ouverte. Réponse 201 avec Location. authorType et \
          submissionId sont refusés (UNKNOWN_FIELD).\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Contact consigné ; en-tête Location = /api/entries/{id} de la séance.",
        headers =
            @Header(
                name = "Location",
                description = "URL de la séance de contact créée, /api/entries/{id}.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description =
            "MALFORMED_REQUEST : identifiant du patient qui n'est pas un UUID, ou corps JSON"
                + " absent ou illisible.",
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
            "VALIDATION_FAILED avec errors[] : modality REQUIRED ou INVALID_VALUE (hors phone,"
                + " email), occurredFrom REQUIRED ou INVALID_FORMAT (date illisible), body"
                + " REQUIRED (texte vide) ; UNKNOWN_FIELD si authorType ou submissionId est"
                + " envoyé.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<EntryDto> recordContact(
      @Parameter(description = "Identifiant du patient.", example = PATIENT_ID_EXAMPLE)
          @PathVariable
          UUID id,
      @Valid @RequestBody RecordContactRequest request) {
    UUID sessionId =
        sessions.contact(
            id,
            new ContactCommand(request.modality(), request.occurredFrom(), request.body(), null));
    return created(sessionId);
  }

  /**
   * Séance courante : l'ouverte, sinon la dernière clôturée, sinon l'accueil. 404
   * NO_CURRENT_SESSION si le patient n'a aucune séance.
   */
  @GetMapping("/api/patients/{id}/sessions/current")
  @Operation(
      summary = "Donne la séance courante du patient",
      description =
          """
          Session praticien, propriétaire du patient. La séance courante est la séance ouverte, \
          sinon la dernière séance ou le dernier contact clôturé, sinon la séance d'accueil. C'est \
          celle à laquelle une entrée sans sessionId est rattachée. L'accueil étant créé de façon \
          asynchrone après la création du patient, il peut manquer brièvement.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Séance courante (entrée de type session)."),
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
        description =
            "PATIENT_NOT_FOUND : patient inexistant ; NO_CURRENT_SESSION : le patient n'a aucune"
                + " séance.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  EntryDto getCurrentSession(
      @Parameter(description = "Identifiant du patient.", example = PATIENT_ID_EXAMPLE)
          @PathVariable
          UUID id) {
    SessionView current =
        sessions
            .current(id)
            .orElseThrow(
                () ->
                    new ClinicalException(
                        ClinicalError.NO_CURRENT_SESSION, "Ce patient n'a aucune séance"));
    return mapper.toDto(queries.get(current.id()));
  }

  private ResponseEntity<EntryDto> created(UUID sessionId) {
    return ResponseEntity.created(URI.create("/api/entries/" + sessionId))
        .body(mapper.toDto(queries.get(sessionId)));
  }
}
