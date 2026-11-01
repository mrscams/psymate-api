package com.psymate.clinical.web;

import com.psymate.clinical.application.EntryCreation;
import com.psymate.clinical.application.EntryDetails;
import com.psymate.clinical.application.EntryQueryService;
import com.psymate.clinical.application.EntryService;
import com.psymate.clinical.application.SessionService;
import com.psymate.clinical.web.EntryCommandFactory.ParsedCreate;
import com.psymate.clinical.web.EntryDtos.CreateEntryRequest;
import com.psymate.clinical.web.EntryDtos.EntryDto;
import com.psymate.clinical.web.EntryDtos.EntryLinkDto;
import com.psymate.clinical.web.EntryDtos.EntryVersionDto;
import com.psymate.clinical.web.EntryDtos.PatchEntryRequest;
import com.psymate.patients.api.PatientOwnershipGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrées du dossier (A5) : créer sur un patient, lire, corriger (version ou grâce), historique,
 * archiver et restaurer. {@code authorType} est en lecture seule, toujours « practitioner » en V1.
 */
@Tag(
    name = "Entrées",
    description =
        "Entrées cliniques d'un dossier patient : création dans une séance, lecture, correction"
            + " versionnée (fenêtre de grâce de 1 h 15), historique des versions, archivage et"
            + " restauration. Rien n'est supprimé physiquement.")
@RestController
class EntryController {

  private static final String PROBLEM = "application/problem+json";
  private static final String ENTRY_ID_EXAMPLE = "3f8e2b1a-6c4d-4e57-9a0b-1d2c3e4f5a6b";

  private final EntryService entries;
  private final SessionService sessions;
  private final EntryQueryService queries;
  private final EntryCommandFactory commands;
  private final EntryMapper mapper;
  private final PatientOwnershipGuard guard;
  private final EntryLinkPresenter linkPresenter;
  private final ClinicalMessages messages;

  EntryController(
      EntryService entries,
      SessionService sessions,
      EntryQueryService queries,
      EntryCommandFactory commands,
      EntryMapper mapper,
      PatientOwnershipGuard guard,
      EntryLinkPresenter linkPresenter,
      ClinicalMessages messages) {
    this.entries = entries;
    this.sessions = sessions;
    this.queries = queries;
    this.commands = commands;
    this.mapper = mapper;
    this.guard = guard;
    this.linkPresenter = linkPresenter;
    this.messages = messages;
  }

  /**
   * Crée une entrée, avec ses liens éventuels dans la même transaction (tout ou rien, M2-02) ; la
   * réponse porte {@code links}. Un corps de type session est routé vers le démarrage de séance
   * (verrou, 409 si une séance est déjà ouverte).
   */
  @PostMapping("/api/patients/{id}/entries")
  @Operation(
      summary = "Crée une entrée clinique dans le dossier du patient",
      description =
          """
          Session praticien, propriétaire du patient. L'entrée appartient à une séance : sessionId \
          explicite, sinon la séance courante du patient (SESSION_REQUIRED s'il n'en a aucune). Un \
          corps de type session démarre une séance (une seule ouverte à la fois). Les liens \
          demandés (links, 20 au plus) sont posés dans la même transaction, tout ou rien. \
          authorType et submissionId sont en lecture seule (UNKNOWN_FIELD). Réponse 201 avec \
          Location et liens posés dans links.\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Entrée créée ; en-tête Location = /api/entries/{id} de la nouvelle entrée.",
        headers =
            @Header(
                name = "Location",
                description = "URL de l'entrée créée, /api/entries/{id}.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description =
            "MALFORMED_REQUEST : identifiant du patient qui n'est pas un UUID, ou corps JSON"
                + " illisible.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description =
            "PATIENT_FORBIDDEN : le patient (ou l'entrée cible d'un lien) appartient à un autre"
                + " praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "PATIENT_NOT_FOUND : patient inexistant.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "SESSION_ALREADY_OPEN : type session demandé alors qu'une séance est déjà ouverte"
                + " pour ce patient.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            """
            VALIDATION_FAILED avec errors[] {field, code, message}, un par champ. Codes : REQUIRED, \
            OUT_OF_RANGE (intensité hors 0 à 10, plus de 20 liens), INVALID_VALUE (session.modality, type, status, \
            visibility, occurred.precision, links[i].direction), INVALID_FORMAT (date ou UUID \
            illisible), INVALID_TIME (occurred.to avant occurred.from), UNKNOWN_EMOTION \
            (feeling.emotionCode), UNKNOWN_TAG (tags[i]), UNKNOWN_FIELD (authorType ou \
            submissionId envoyé), DETAIL_NOT_ALLOWED (feeling, medication ou session d'un autre \
            type), NOT_ALLOWED_ON_CREATE (title, body, tags, links, session.durationMin sur une \
            séance), SESSION_NOT_ALLOWED (sessionId sur une séance). Liens refusés (champ \
            links[i].linkType ou links[i].targetId, allowedLinkTypes = liste de codes de type, ex. \
            ["caused_by","co_occurs"], pour LINK_TYPE_NOT_ALLOWED) : \
            ENTRY_NOT_FOUND, SELF_LINK, DIFFERENT_PATIENT, UNKNOWN_LINK_TYPE, \
            LINK_TYPE_NOT_ALLOWED, LINK_EXISTS, ENTRY_ARCHIVED. Codes portés par le code racine, \
            errors[0].field renseigné : SESSION_REQUIRED et SESSION_INVALID (sessionId), \
            INVALID_TIME (occurred : période sans libellé ou sans fin, phase de vie inconnue ou \
            patient sans date de naissance).\
            """,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<EntryDto> createEntry(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @RequestBody CreateEntryRequest request,
      HttpServletRequest http) {
    guard.requireOwned(id);
    ParsedCreate parsed = commands.parseCreate(request);
    if (parsed.start() != null) {
      return created(sessions.start(id, parsed.start()), List.of());
    }
    EntryCreation creation = entries.createWithLinks(id, parsed.command(), parsed.links());
    List<EntryLinkDto> links =
        linkPresenter.present(creation.id(), creation.links(), messages.localeOf(http));
    return created(creation.id(), links);
  }

  @GetMapping("/api/entries/{id}")
  @Operation(
      summary = "Lit une entrée avec sa version courante, son détail et ses tags",
      description =
          """
          Session praticien, propriétaire du patient de l'entrée. Une entrée archivée reste lisible \
          (archivedAt renseigné). Les liens ne sont pas inclus (links est null) : voir \
          GET /api/entries/{id}/links. authorType est en lecture seule.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Entrée complète."),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : identifiant de l'entrée qui n'est pas un UUID.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : l'entrée appartient au dossier d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ENTRY_NOT_FOUND : entrée inexistante.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  EntryDto getEntry(
      @Parameter(description = "Identifiant de l'entrée.", example = ENTRY_ID_EXAMPLE) @PathVariable
          UUID id) {
    return mapper.toDto(queries.get(id));
  }

  /** Correction partielle : nouvelle version, ou modification en place pendant la grâce (A4.8). */
  @PatchMapping("/api/entries/{id}")
  @Operation(
      summary = "Corrige une entrée par une nouvelle version ou en place pendant la grâce",
      description =
          """
          Session praticien, propriétaire du patient de l'entrée. Les champs absents sont \
          inchangés ; occurred et tags sont remplacés en entier, les détails fusionnés champ par \
          champ. Règle de grâce de 1 h 15 après la création : même auteur, entrée sans lien ni \
          autre version, la modification est faite en place (pas de nouvelle version) ; sinon une \
          nouvelle version est créée (reason conservé). Un changement de tags seul ou sans effet \
          ne crée jamais de version. Une entrée archivée ne se corrige pas. authorType, \
          submissionId et links sont refusés.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Entrée corrigée, version courante à jour."),
    @ApiResponse(
        responseCode = "400",
        description =
            "MALFORMED_REQUEST : identifiant de l'entrée qui n'est pas un UUID, ou corps JSON"
                + " illisible.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : l'entrée appartient au dossier d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ENTRY_NOT_FOUND : entrée inexistante.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "ENTRY_ARCHIVED : l'entrée est archivée, la restaurer d'abord.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            """
            VALIDATION_FAILED avec errors[] {field, code, message}. Codes : REQUIRED (titre d'un \
            event ou d'une situation, texte d'une note, champ minimal du détail), OUT_OF_RANGE, \
            INVALID_VALUE (status, visibility, occurred.precision), INVALID_FORMAT (date ou UUID \
            illisible), INVALID_TIME (occurred.to avant occurred.from), UNKNOWN_EMOTION \
            (feeling.emotionCode), UNKNOWN_TAG (tags[i]), UNKNOWN_FIELD (authorType ou \
            submissionId envoyé), NOT_SUPPORTED_ON_PATCH (links envoyé), DETAIL_NOT_ALLOWED \
            (détail d'un autre type que l'entrée). Code racine INVALID_TIME, errors[0].field = \
            occurred : période sans libellé ou sans fin, phase de vie inconnue ou patient sans \
            date de naissance.\
            """,
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  EntryDto patchEntry(
      @Parameter(description = "Identifiant de l'entrée.", example = ENTRY_ID_EXAMPLE) @PathVariable
          UUID id,
      @RequestBody PatchEntryRequest request) {
    EntryDetails current = queries.get(id);
    entries.correct(id, commands.parsePatch(request, current));
    return mapper.toDto(queries.get(id));
  }

  /** Versions de la plus récente à la plus ancienne. */
  @GetMapping("/api/entries/{id}/versions")
  @Operation(
      summary = "Liste les versions d'une entrée, de la plus récente à la plus ancienne",
      description =
          """
          Session praticien, propriétaire du patient de l'entrée. Renvoie toutes les versions, sans \
          pagination, y compris pour une entrée archivée. Une modification faite en place pendant \
          la fenêtre de grâce de 1 h 15 ne crée pas de version.\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Versions de l'entrée, la plus récente en premier."),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : identifiant de l'entrée qui n'est pas un UUID.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : l'entrée appartient au dossier d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ENTRY_NOT_FOUND : entrée inexistante.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  List<EntryVersionDto> listEntryVersions(
      @Parameter(description = "Identifiant de l'entrée.", example = ENTRY_ID_EXAMPLE) @PathVariable
          UUID id) {
    return mapper.toVersionDtos(queries.history(id));
  }

  /** Archive l'entrée (suppression logique) : 204. */
  @DeleteMapping("/api/entries/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Archive une entrée sans la supprimer physiquement",
      description =
          """
          Session praticien, propriétaire du patient de l'entrée. Suppression logique : l'entrée \
          reste lisible (archivedAt) et restaurable, mais sort de la chronologie, de l'export et \
          des corrections. Idempotent : archiver une entrée déjà archivée renvoie aussi 204.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Entrée archivée (ou déjà archivée)."),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : identifiant de l'entrée qui n'est pas un UUID.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : l'entrée appartient au dossier d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ENTRY_NOT_FOUND : entrée inexistante.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  void archiveEntry(
      @Parameter(description = "Identifiant de l'entrée.", example = ENTRY_ID_EXAMPLE) @PathVariable
          UUID id) {
    entries.archive(id);
  }

  @PostMapping("/api/entries/{id}/restore")
  @Operation(
      summary = "Restaure une entrée archivée",
      description =
          """
          Session praticien, propriétaire du patient de l'entrée. L'entrée redevient visible et \
          corrigeable (archivedAt null). Idempotent : restaurer une entrée non archivée renvoie 200 \
          sans rien changer. Sans corps de requête.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Entrée restaurée (archivedAt null)."),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : identifiant de l'entrée qui n'est pas un UUID.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : l'entrée appartient au dossier d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ENTRY_NOT_FOUND : entrée inexistante.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  EntryDto restoreEntry(
      @Parameter(description = "Identifiant de l'entrée.", example = ENTRY_ID_EXAMPLE) @PathVariable
          UUID id) {
    entries.restore(id);
    return mapper.toDto(queries.get(id));
  }

  private ResponseEntity<EntryDto> created(UUID entryId, List<EntryLinkDto> links) {
    EntryDto dto = mapper.toDto(queries.get(entryId));
    return ResponseEntity.created(URI.create("/api/entries/" + entryId))
        .body(mapper.withLinks(dto, links));
  }
}
