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
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
@RestController
class EntryController {

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
  ResponseEntity<EntryDto> createEntry(
      @PathVariable UUID id, @RequestBody CreateEntryRequest request, HttpServletRequest http) {
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
  EntryDto getEntry(@PathVariable UUID id) {
    return mapper.toDto(queries.get(id));
  }

  /** Correction partielle : nouvelle version, ou modification en place pendant la grâce (A4.8). */
  @PatchMapping("/api/entries/{id}")
  EntryDto patchEntry(@PathVariable UUID id, @RequestBody PatchEntryRequest request) {
    EntryDetails current = queries.get(id);
    entries.correct(id, commands.parsePatch(request, current));
    return mapper.toDto(queries.get(id));
  }

  /** Versions de la plus récente à la plus ancienne. */
  @GetMapping("/api/entries/{id}/versions")
  List<EntryVersionDto> listEntryVersions(@PathVariable UUID id) {
    return mapper.toVersionDtos(queries.history(id));
  }

  /** Archive l'entrée (suppression logique) : 204. */
  @DeleteMapping("/api/entries/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void archiveEntry(@PathVariable UUID id) {
    entries.archive(id);
  }

  @PostMapping("/api/entries/{id}/restore")
  EntryDto restoreEntry(@PathVariable UUID id) {
    entries.restore(id);
    return mapper.toDto(queries.get(id));
  }

  private ResponseEntity<EntryDto> created(UUID entryId, List<EntryLinkDto> links) {
    EntryDto dto = mapper.toDto(queries.get(entryId));
    return ResponseEntity.created(URI.create("/api/entries/" + entryId))
        .body(mapper.withLinks(dto, links));
  }
}
