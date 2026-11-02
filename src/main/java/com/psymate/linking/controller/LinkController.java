package com.psymate.linking.controller;

import com.psymate.linking.dto.ChangeLinkTypeRequest;
import com.psymate.linking.dto.CreateLinkRequest;
import com.psymate.linking.dto.LinkAssembler;
import com.psymate.linking.dto.LinkDto;
import com.psymate.linking.dto.LinkMessages;
import com.psymate.linking.model.Link;
import com.psymate.linking.service.LinkService;
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
import java.util.List;
import java.util.Locale;
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
 * Liens entre entrées (A5, écran 6) : poser un lien depuis une entrée, changer son type, le
 * supprimer, lister les liens d'une entrée. Un lien se pose toujours du côté de l'entrée ouverte :
 * {@code direction} choisit si elle en est la source (outgoing) ou la cible (incoming). Le serveur
 * ne stocke jamais de type passif : « déclenché par » est un lien triggers de sens inversé.
 */
@Tag(
    name = "Liens",
    description =
        "Liens entre entrées d'un même patient : pose depuis une entrée, changement de type"
            + " (versionné), suppression logique et liste des liens d'une entrée.")
@RestController
class LinkController {

  private static final String INCOMING = "incoming";

  private final LinkService links;
  private final LinkAssembler assembler;
  private final LinkMessages messages;

  LinkController(LinkService links, LinkAssembler assembler, LinkMessages messages) {
    this.links = links;
    this.assembler = assembler;
    this.messages = messages;
  }

  /** Pose un lien manuel accepté ; 201 avec le lien vu depuis l'entrée de l'URL. */
  @PostMapping("/api/entries/{id}/links")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Pose un lien manuel entre l'entrée ouverte et une autre entrée du même patient",
      description =
          """
          Session praticien propriétaire du patient des deux entrées. Le lien est créé avec la \
          provenance manual et le statut accepted (non modifiables). direction (outgoing par \
          défaut) place l'entrée de l'URL comme source (outgoing) ou comme cible (incoming) ; la \
          réponse est le lien vu depuis cette entrée (label et sentence dans la langue \
          Accept-Language, fr par défaut). Les deux entrées doivent être du même patient et non \
          archivées, et le type autorisé pour le couple (type source, type cible) par le catalogue \
          GET /api/vocabulary/link-types. Un lien actif identique (mêmes extrémités et même type, \
          sens indifférent pour un type symétrique) est refusé. Limite : rationale 500 caractères \
          au plus. Réponse 201 avec Location.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Lien créé ; en-tête Location = /api/links/{id}.",
        headers = @Header(name = "Location", description = "URI du lien créé, /api/links/{id}.")),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : corps JSON illisible sans champ identifiable.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description =
            "PATIENT_FORBIDDEN : une des deux entrées appartient à un patient d'un autre"
                + " praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ENTRY_NOT_FOUND : l'entrée de l'URL ou la cible targetId n'existe pas.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "LINK_EXISTS : un lien actif identique existe déjà, existingLinkId donne son"
                + " identifiant ; ENTRY_ARCHIVED : une extrémité est archivée, errors[] porte le"
                + " champ sourceId ou targetId.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ : REQUIRED"
                + " (targetId, linkType), INVALID_VALUE (direction autre que incoming ou"
                + " outgoing), OUT_OF_RANGE (rationale de plus de 500 caractères), INVALID_FORMAT"
                + " (targetId qui n'est pas un UUID) ; SELF_LINK (targetId : même entrée que"
                + " celle de l'URL) ; DIFFERENT_PATIENT (targetId : entrée d'un autre patient) ;"
                + " UNKNOWN_LINK_TYPE (linkType absent du catalogue ou inactif) ;"
                + " LINK_TYPE_NOT_ALLOWED (linkType interdit pour ce couple de types d'entrées :"
                + " allowedLinkTypes[] {code, label} liste les types possibles, vide si aucun).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<LinkDto> createLink(
      @Parameter(
              description = "Identifiant de l'entrée ouverte, dont le lien est vu.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @Valid @RequestBody CreateLinkRequest request,
      HttpServletRequest http) {
    boolean incoming = INCOMING.equals(request.direction());
    UUID sourceId = incoming ? request.targetId() : id;
    UUID targetId = incoming ? id : request.targetId();
    Link link = links.create(sourceId, targetId, request.linkType(), request.rationale());
    return ResponseEntity.created(URI.create("/api/links/" + link.id()))
        .body(assembler.present(link, id, messages.localeOf(http)));
  }

  /**
   * Change le type : un nouveau lien remplace l'ancien (superseded). Le lien rendu est le nouveau,
   * vu depuis sa source.
   */
  @PatchMapping("/api/links/{id}")
  @Operation(
      summary = "Change le type d'un lien en le remplaçant par un nouveau lien",
      description =
          """
          Session praticien propriétaire du patient du lien. Le lien doit être actif (409 \
          LINK_NOT_ACTIVE sinon) et ses entrées non archivées. Un nouveau lien manual accepted est \
          créé avec les mêmes extrémités et la même justification, l'ancien passe superseded \
          (jamais effacé) : la réponse est le nouveau lien, avec un nouvel identifiant, vu depuis \
          sa source. Les règles de matrice des types et de doublon sont celles de la création ; \
          reposer le type actuel donne 409 LINK_EXISTS.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Nouveau lien créé en remplacement de l'ancien."),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : corps JSON illisible sans champ identifiable.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : le lien appartient à un patient d'un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "LINK_NOT_FOUND : lien inexistant.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "LINK_NOT_ACTIVE : le lien est déjà remplacé ou supprimé ; LINK_EXISTS : un lien actif"
                + " de ce type existe déjà entre les mêmes entrées (y compris le type actuel),"
                + " existingLinkId donne son identifiant ; ENTRY_ARCHIVED : une extrémité est"
                + " archivée, errors[] porte le champ sourceId ou targetId.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ : REQUIRED"
                + " (linkType) ; UNKNOWN_LINK_TYPE (linkType absent du catalogue ou inactif) ;"
                + " LINK_TYPE_NOT_ALLOWED (linkType interdit pour ce couple de types d'entrées :"
                + " allowedLinkTypes[] {code, label} liste les types possibles, vide si aucun).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  LinkDto changeLinkType(
      @Parameter(
              description = "Identifiant du lien à remplacer.",
              example = "5d2f7a10-8c4b-4e6a-b1d3-7f9e0a2c4b68")
          @PathVariable
          UUID id,
      @Valid @RequestBody ChangeLinkTypeRequest request,
      HttpServletRequest http) {
    Link link = links.changeType(id, request.linkType());
    return assembler.present(link, link.sourceId(), messages.localeOf(http));
  }

  /** Supprime le lien (il passe superseded, jamais effacé) : 204. */
  @DeleteMapping("/api/links/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Supprime un lien sans l'effacer",
      description =
          """
          Session praticien propriétaire du patient du lien. Le lien passe superseded (jamais \
          supprimé physiquement) et ses deux entrées perdent un lien dans linkCount. Idempotent : \
          supprimer un lien déjà remplacé ou supprimé renvoie aussi 204, sans effet.""")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Lien supprimé (ou déjà inactif)."),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : le lien appartient à un patient d'un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "LINK_NOT_FOUND : lien inexistant.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  void removeLink(
      @Parameter(
              description = "Identifiant du lien à supprimer.",
              example = "5d2f7a10-8c4b-4e6a-b1d3-7f9e0a2c4b68")
          @PathVariable
          UUID id) {
    links.remove(id);
  }

  /** Liens acceptés de l'entrée, chacun avec son verbe et sa phrase vus depuis cette entrée. */
  @GetMapping("/api/entries/{id}/links")
  @Operation(
      summary = "Liste les liens acceptés d'une entrée",
      description =
          """
          Session praticien propriétaire du patient de l'entrée. Rend les liens acceptés dont \
          l'entrée est source ou cible, du plus ancien au plus récent (createdAt, puis id), chacun \
          avec label et sentence vus depuis cette entrée (verbe inverse quand elle est la cible \
          d'un lien dirigé), dans la langue Accept-Language (fr par défaut). Les liens remplacés \
          ou supprimés n'y figurent pas. Liste complète, non paginée, vide si l'entrée n'a pas de \
          lien.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Liens acceptés de l'entrée (tableau, éventuellement vide)."),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : l'entrée appartient à un patient d'un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ENTRY_NOT_FOUND : entrée inexistante.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  List<LinkDto> listEntryLinks(
      @Parameter(
              description = "Identifiant de l'entrée dont on liste les liens.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      HttpServletRequest http) {
    Locale locale = messages.localeOf(http);
    return links.acceptedLinksOf(id).stream().map(l -> assembler.present(l, id, locale)).toList();
  }
}
