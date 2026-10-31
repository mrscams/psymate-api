package com.psymate.directory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Formulaire de contact de l'annuaire public : sans authentification, 5 envois / h / IP. */
@RestController
@RequestMapping("/api/public/directory")
@Tag(name = "public-directory-controller")
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
          "Répond 202 sans corps pour tout envoi bien formé, y compris un envoi ignoré en silence"
              + " (honeypot, formulaire soumis en moins de 3 s, fiche inconnue ou non publiée).")
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "Accepté (ou ignoré en silence)"),
    @ApiResponse(
        responseCode = "422",
        description = "Champs invalides (errors[] par champ)",
        content =
            @io.swagger.v3.oas.annotations.media.Content(
                mediaType = "application/problem+json",
                schema =
                    @io.swagger.v3.oas.annotations.media.Schema(
                        implementation = org.springframework.http.ProblemDetail.class))),
    @ApiResponse(
        responseCode = "429",
        description = "Plus de 5 envois par heure depuis cette adresse (en-tête Retry-After)",
        content =
            @io.swagger.v3.oas.annotations.media.Content(
                mediaType = "application/problem+json",
                schema =
                    @io.swagger.v3.oas.annotations.media.Schema(
                        implementation = org.springframework.http.ProblemDetail.class)))
  })
  ResponseEntity<Void> submitContactRequest(
      @PathVariable String slug,
      @Valid @RequestBody SubmitContactRequest request,
      HttpServletRequest http) {
    service.submit(slug, request, http.getRemoteAddr());
    return ResponseEntity.accepted().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
  }
}
