package com.psymate.clinical.web;

import com.psymate.clinical.api.SessionView;
import com.psymate.clinical.application.CloseSessionCommand;
import com.psymate.clinical.application.ContactCommand;
import com.psymate.clinical.application.EntryQueryService;
import com.psymate.clinical.application.SessionService;
import com.psymate.clinical.application.StartSessionCommand;
import com.psymate.clinical.domain.ClinicalError;
import com.psymate.clinical.domain.ClinicalException;
import com.psymate.clinical.web.EntryDtos.CloseSessionRequest;
import com.psymate.clinical.web.EntryDtos.EntryDto;
import com.psymate.clinical.web.EntryDtos.RecordContactRequest;
import com.psymate.clinical.web.EntryDtos.StartSessionRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Séances (UC-04, F11) : démarrer, clôturer, consigner un contact, séance courante. */
@RestController
class SessionController {

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
  ResponseEntity<EntryDto> startSession(
      @PathVariable UUID id, @Valid @RequestBody StartSessionRequest request) {
    UUID sessionId = sessions.start(id, new StartSessionCommand(request.modality(), null));
    return created(sessionId);
  }

  /** Clôture la séance : durée, fin et compte rendu figés dans une version. */
  @PostMapping("/api/sessions/{id}/close")
  EntryDto closeSession(@PathVariable UUID id, @Valid @RequestBody CloseSessionRequest request) {
    sessions.close(id, new CloseSessionCommand(request.durationMin(), request.summary()));
    return mapper.toDto(queries.get(id));
  }

  /** Contact hors rendez-vous (téléphone, e-mail), consigné comme une séance déjà clôturée. */
  @PostMapping("/api/patients/{id}/sessions/contact")
  ResponseEntity<EntryDto> recordContact(
      @PathVariable UUID id, @Valid @RequestBody RecordContactRequest request) {
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
  EntryDto getCurrentSession(@PathVariable UUID id) {
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
