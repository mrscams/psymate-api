package com.psymate.identity.auth.controller;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.auth.dto.MeSecurityAssembler;
import com.psymate.identity.auth.dto.MeSecurityResponse;
import com.psymate.identity.auth.model.SessionNotFoundException;
import com.psymate.identity.auth.service.SessionSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Sécurité du compte",
    description =
        "Sessions actives du praticien, dernière connexion et révocation d'une session"
            + " (fiche 18).")
@RestController
@RequestMapping("/api/me")
class MeSecurityController {

  private final SessionSecurityService service;

  MeSecurityController(SessionSecurityService service) {
    this.service = service;
  }

  @GetMapping("/security")
  @Operation(
      operationId = "getMeSecurity",
      summary = "Sessions actives et connexion précédente du praticien connecté",
      description =
          """
          Session praticien. Liste les sessions non expirées du praticien (jamais celles d'un \
          autre compte), la plus récemment utilisée d'abord, avec l'appareil déduit de \
          l'User-Agent, les dates d'ouverture, de dernier appel et d'expiration, et current sur la \
          session qui porte l'appel ; lastLogin donne la connexion précédente (la dernière \
          connexion réussie antérieure à l'ouverture de la session courante, null à la première \
          connexion ou pour une session ouverte avant cette fonction). Aucune adresse IP, aucun \
          User-Agent brut ni identifiant du cookie n'est rendu. Les sessions ouvertes avant cette \
          fonction et les anciennes connexions ont device = null. La ville n'est pas renseignée \
          en V1 : city vaut toujours null. Révoquer une session : \
          POST /api/me/sessions/{id}/revoke.\
          """)
  @ApiResponses(@ApiResponse(responseCode = "200", description = "Sécurité du compte."))
  MeSecurityResponse security(
      @AuthenticationPrincipal CurrentPractitioner current, HttpServletRequest request) {
    return MeSecurityAssembler.toResponse(
        service.overview(current.id(), request.getSession().getId()));
  }

  @PostMapping("/sessions/{id}/revoke")
  @Operation(
      operationId = "revokeSession",
      summary = "Révoque une autre session du praticien",
      description =
          """
          Session praticien. Ferme la session désignée (id lu dans GET /api/me/security) : une \
          requête avec son ancien cookie reçoit aussitôt 401. L'opération est consignée \
          (session_revoked). 404 si l'id est inconnu, malformé, expiré ou appartient à un autre \
          compte (même réponse dans tous les cas) ; 409 si c'est la session courante : utiliser \
          POST /api/auth/logout.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Session révoquée."),
    @ApiResponse(
        responseCode = "404",
        description = "SESSION_NOT_FOUND : session inconnue, expirée ou d'un autre compte.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "CURRENT_SESSION : c'est la session qui porte l'appel, utiliser POST"
                + " /api/auth/logout.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> revoke(
      @AuthenticationPrincipal CurrentPractitioner current,
      @Parameter(
              description = "Identifiant opaque d'une session de GET /api/me/security.",
              schema = @Schema(type = "string", format = "uuid"))
          @PathVariable
          String id,
      HttpServletRequest request) {
    service.revoke(current.id(), parse(id), request.getSession().getId(), request.getRemoteAddr());
    return ResponseEntity.noContent().build();
  }

  /** Un id mal formé ne désigne aucune session : 404, comme un id inconnu. */
  private static UUID parse(String id) {
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new SessionNotFoundException();
    }
  }
}
