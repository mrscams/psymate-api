package com.psymate.linking.web;

import com.psymate.linking.application.LinkService;
import com.psymate.linking.domain.Link;
import com.psymate.linking.web.LinkDtos.ChangeLinkTypeRequest;
import com.psymate.linking.web.LinkDtos.CreateLinkRequest;
import com.psymate.linking.web.LinkDtos.LinkDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Locale;
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
 * Liens entre entrées (A5, écran 6) : poser un lien depuis une entrée, changer son type, le
 * supprimer, lister les liens d'une entrée. Un lien se pose toujours du côté de l'entrée ouverte :
 * {@code direction} choisit si elle en est la source (outgoing) ou la cible (incoming). Le serveur
 * ne stocke jamais de type passif : « déclenché par » est un lien triggers de sens inversé.
 */
@RestController
class LinkController {

  private static final String INCOMING = "incoming";

  private final LinkService links;
  private final LinkPresenter presenter;
  private final LinkMessages messages;

  LinkController(LinkService links, LinkPresenter presenter, LinkMessages messages) {
    this.links = links;
    this.presenter = presenter;
    this.messages = messages;
  }

  /** Pose un lien manuel accepté ; 201 avec le lien vu depuis l'entrée de l'URL. */
  @PostMapping("/api/entries/{id}/links")
  @ResponseStatus(HttpStatus.CREATED)
  ResponseEntity<LinkDto> createLink(
      @PathVariable UUID id,
      @Valid @RequestBody CreateLinkRequest request,
      HttpServletRequest http) {
    boolean incoming = INCOMING.equals(request.direction());
    UUID sourceId = incoming ? request.targetId() : id;
    UUID targetId = incoming ? id : request.targetId();
    Link link = links.create(sourceId, targetId, request.linkType(), request.rationale());
    return ResponseEntity.created(URI.create("/api/links/" + link.id()))
        .body(presenter.present(link, id, messages.localeOf(http)));
  }

  /**
   * Change le type : un nouveau lien remplace l'ancien (superseded). Le lien rendu est le nouveau,
   * vu depuis sa source.
   */
  @PatchMapping("/api/links/{id}")
  LinkDto changeLinkType(
      @PathVariable UUID id,
      @Valid @RequestBody ChangeLinkTypeRequest request,
      HttpServletRequest http) {
    Link link = links.changeType(id, request.linkType());
    return presenter.present(link, link.sourceId(), messages.localeOf(http));
  }

  /** Supprime le lien (il passe superseded, jamais effacé) : 204. */
  @DeleteMapping("/api/links/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeLink(@PathVariable UUID id) {
    links.remove(id);
  }

  /** Liens acceptés de l'entrée, chacun avec son verbe et sa phrase vus depuis cette entrée. */
  @GetMapping("/api/entries/{id}/links")
  List<LinkDto> listEntryLinks(@PathVariable UUID id, HttpServletRequest http) {
    Locale locale = messages.localeOf(http);
    return links.acceptedLinksOf(id).stream().map(l -> presenter.present(l, id, locale)).toList();
  }
}
