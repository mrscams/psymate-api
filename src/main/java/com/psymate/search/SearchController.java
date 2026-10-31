package com.psymate.search;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.search.EntrySearchService.Request;
import com.psymate.search.SearchDtos.EntrySearchResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Recherche dans le dossier d'un patient (A6, écran 7). Exception documentée à la pagination par
 * curseur du reste de l'API : pagination par numéro de page, 20 résultats par page, comme
 * l'annuaire public, parce que le classement par pertinence n'a pas de clé stable pour un curseur.
 */
@RestController
@Tag(name = "search")
class SearchController {

  private final EntrySearchService service;
  private final PatientOwnershipGuard guard;

  SearchController(EntrySearchService service, PatientOwnershipGuard guard) {
    this.service = service;
    this.guard = guard;
  }

  @GetMapping("/api/patients/{id}/search")
  @Operation(
      operationId = "searchPatientEntries",
      summary = "Recherche dans le dossier d'un patient",
      description =
          "Recherche plein texte (français, sans accents, ts_rank_cd, boost x1,5 sous 90 jours),"
              + " repli par trigrammes (résultats approchés, approximate=true) quand moins de 3"
              + " résultats exacts, extraits surlignés (balises mark), filtres et facettes. Le"
              + " paramètre q accepte les jetons tag:xxx, émotion:xxx, avant:AAAA[-MM[-JJ]] et"
              + " après:AAAA[-MM[-JJ]] (valeur entre guillemets possible) ; ils se cumulent avec"
              + " les paramètres types, emotions, tags, from et to. Plusieurs valeurs d'un même"
              + " filtre s'additionnent (ou), les filtres entre eux se combinent (et). Chaque"
              + " facette ignore son propre filtre. Exception documentée à la pagination par"
              + " curseur : pagination par numéro de page, 20 résultats par page (page 1 : exacts"
              + " puis approchés ; pages suivantes : exacts seulement). Les entrées privées d'un"
              + " autre praticien ne sont jamais renvoyées.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Résultats, facettes et requête interprétée"),
    @ApiResponse(
        responseCode = "400",
        description = "Paramètre invalide (errors[] par champ, code SEARCH_BAD_REQUEST)",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Le patient n'appartient pas au praticien courant",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Patient inconnu",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  EntrySearchResultDto searchPatientEntries(
      @Parameter(description = "Identifiant du patient") @PathVariable("id") UUID patientId,
      @Parameter(
              description =
                  "Texte et jetons de recherche, 200 caractères au maximum. Exemple :"
                      + " licenciement tag:travail émotion:anxiété après:2026-06",
              schema = @Schema(maxLength = 200))
          @RequestParam(required = false)
          String q,
      @Parameter(
              description =
                  "Types d'entrée à retenir (note, event, situation, feeling, session, medication) ; paramètre répétable")
          @RequestParam(required = false)
          List<String> types,
      @Parameter(description = "Codes d'émotion à retenir (ressentis) ; paramètre répétable")
          @RequestParam(required = false)
          List<String> emotions,
      @Parameter(
              description =
                  "Libellés de tags à retenir, sans accents ni casse ; paramètre répétable")
          @RequestParam(required = false)
          List<String> tags,
      @Parameter(
              description =
                  "Début de période sur la date de l'entrée : instant ISO 8601 ou date AAAA-MM-JJ")
          @RequestParam(required = false)
          String from,
      @Parameter(
              description =
                  "Fin de période (incluse) : instant ISO 8601 ou date AAAA-MM-JJ (jour inclus)")
          @RequestParam(required = false)
          String to,
      @Parameter(description = "Séance de rattachement des entrées") @RequestParam(required = false)
          UUID sessionId,
      @Parameter(description = "Inclure les entrées archivées (exclues par défaut)")
          @RequestParam(defaultValue = "false")
          boolean includeArchived,
      @Parameter(description = "Numéro de page, à partir de 1 (20 résultats par page)")
          @RequestParam(defaultValue = "1")
          int page) {
    guard.requireOwned(patientId);
    CurrentPractitioner me =
        CurrentPractitioner.get()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    return service.search(
        me.tenantId(),
        me.id(),
        patientId,
        new Request(q, types, emotions, tags, from, to, sessionId, includeArchived, page));
  }
}
