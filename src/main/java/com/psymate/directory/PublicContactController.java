package com.psymate.directory;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Formulaire de contact de l'annuaire public : sans authentification, 5 envois / h / IP. */
@RestController
@RequestMapping("/api/public/directory")
@Tag(
    name = "Contact public",
    description =
        "Formulaire de contact de l'annuaire public : message d'un visiteur à un praticien publié,"
            + " relayé par e-mail.")
class PublicContactController {

  private final ContactRequestService service;

  PublicContactController(ContactRequestService service) {
    this.service = service;
  }

  @PostMapping("/practitioners/{slug}/contact")
  @Operation(
      operationId = "submitContactRequest",
      summary = "Envoie un message au praticien (relayé par e-mail, non rattaché à un dossier)",
      description =
          """
          Public, sans session ; limité à 5 envois par heure et par IP (429 RATE_LIMITED avec \
          Retry-After), en plus de 60 requêtes par minute et par IP. Répond 202 sans corps pour \
          tout envoi bien formé, y compris un envoi ignoré en silence et sans trace : honeypot \
          rempli, formulaire soumis en moins de 3 secondes ou dans le futur, profil inconnu ou \
          non publié, praticien sans adresse de réception. Sinon le message est enregistré puis \
          relayé par e-mail à l'adresse de contact du profil, à défaut à celle du compte ; un \
          échec d'envoi ne remonte jamais au visiteur. Les demandes enregistrées sont purgées \
          après 30 jours.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Accepté, ou ignoré en silence ; aucun corps.",
        headers =
            @Header(
                name = "Cache-Control",
                description = "no-store.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_BODY : corps JSON illisible (syntaxe ou type de champ invalide).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, message} (pas de code par champ) ; champs :"
                + " name, email, message (absent, vide ou trop long), email (adresse invalide),"
                + " subject (plus de 200 caractères), consent (absent ou différent de true),"
                + " formShownAt (absent).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "429",
        description =
            "RATE_LIMITED : plus de 5 envois par heure depuis cette adresse IP ; en-tête"
                + " Retry-After et champ retryAfterSeconds en secondes.",
        headers =
            @Header(
                name = "Retry-After",
                description = "Secondes à attendre avant un nouvel envoi.",
                schema = @Schema(type = "integer")),
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> submitContactRequest(
      @Parameter(
              description = "Adresse publique (slug) du praticien destinataire.",
              example = "claire-lambert")
          @PathVariable
          String slug,
      @Valid @RequestBody SubmitContactRequest request,
      HttpServletRequest http) {
    service.submit(slug, request, http.getRemoteAddr());
    return ResponseEntity.accepted().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
  }
}
