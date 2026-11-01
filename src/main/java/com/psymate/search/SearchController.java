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
@Tag(
    name = "Recherche",
    description =
        "Recherche plein texte, filtres et facettes dans les entrées du dossier d'un patient.")
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
      summary = "Recherche des entrées dans le dossier d'un patient",
      description =
          """
          Session praticien propriétaire du patient. Recherche plein texte en français, sans \
          accents ni casse, classée par pertinence avec un boost x1,5 pour les entrées de moins de \
          90 jours ; sans texte, liste par date décroissante. Avec un texte (sans exclusion -mot) et moins de 3 résultats \
          exacts, la page 1 est complétée par des résultats approchés (tolérance aux fautes, approximate=true). \
          Extraits échappés en HTML avec <mark>. Syntaxe de q, filtres et facettes (chacune ignore \
          son propre filtre) : voir les paramètres ; plusieurs valeurs d'un même filtre se \
          combinent en « ou », les filtres entre eux en « et ». Les entrées privées d'un autre \
          praticien ne sont jamais renvoyées. Pagination par numéro de page (exception au curseur) \
          : 20 résultats par page, 500 pages au plus ; approchés sur la page 1 seulement.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Résultats de la page, total exact, facettes et requête interprétée."),
    @ApiResponse(
        responseCode = "400",
        description =
            "SEARCH_BAD_REQUEST : errors[] {field, code, message}, toutes les erreurs ensemble ;"
                + " codes par champ : q = TOO_LONG (plus de 200 caractères) ou INVALID_DATE (date"
                + " invalide dans avant: ou après:), page = INVALID_PAGE (hors 1..500), from et"
                + " to = INVALID_DATE, to = RANGE_INVERTED (to antérieur à from),"
                + " types = UNKNOWN_TYPE, et INVALID_FORMAT sur le paramètre concerné pour une"
                + " valeur non convertible (page non numérique, sessionId qui n'est pas un UUID,"
                + " includeArchived non booléen, id qui n'est pas un UUID : field = id). Le"
                + " contrôle de propriété passe avant les contrôles de q, page, from, to et types"
                + " (patient d'un autre praticien : 403), mais pas avant INVALID_FORMAT, détecté"
                + " à la conversion des paramètres.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "PATIENT_NOT_FOUND : aucun patient avec cet identifiant.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  EntrySearchResultDto searchPatientEntries(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable("id")
          UUID patientId,
      @Parameter(
              description =
                  "Texte et jetons de recherche, 200 caractères au maximum (400 TOO_LONG au-delà)."
                      + " Le texte suit la syntaxe web : mots (tous requis), \"expression exacte\","
                      + " mot1 or mot2, -mot pour exclure. Jetons retirés du texte et appliqués"
                      + " comme filtres : tag:travail, émotion:anxiété (code ou libellé,"
                      + " emotion: accepté), avant:AAAA[-MM[-JJ]] (strictement avant le début de"
                      + " la période, before: accepté), après:AAAA[-MM[-JJ]] (à partir de la fin de"
                      + " la période, after: accepté ; après:2026-06 = dès le 1er juillet 2026,"
                      + " UTC). Valeur à espaces entre guillemets : tag:\"vie pro\". Préfixes sans"
                      + " casse ni accents ; un tag ou une émotion inconnus ne retiennent aucune"
                      + " entrée, sans erreur.",
              schema = @Schema(maxLength = 200),
              example = "licenciement tag:travail émotion:anxiété après:2026-06")
          @RequestParam(required = false)
          String q,
      @Parameter(
              description =
                  "Types d'entrée à retenir ; paramètre répétable (types=note&types=event), en"
                      + " « ou ». Valeurs : note, event, situation, feeling, session, medication ; toute autre donne 400 UNKNOWN_TYPE.")
          @RequestParam(required = false)
          List<String> types,
      @Parameter(
              description =
                  "Émotions à retenir (ressentis), par code ou libellé sans casse ni accents ;"
                      + " paramètre répétable, en « ou ». Une émotion inconnue ne retient aucune"
                      + " entrée.")
          @RequestParam(required = false)
          List<String> emotions,
      @Parameter(
              description =
                  "Libellés de tags à retenir, sans casse ni accents, « # » de tête toléré ;"
                      + " paramètre répétable, en « ou ». Un tag inconnu ne retient aucune entrée.")
          @RequestParam(required = false)
          List<String> tags,
      @Parameter(
              description =
                  "Début de période sur la date de l'entrée (occurred_from), inclus : date"
                      + " AAAA-MM-JJ (dès 00:00 UTC) ou instant ISO 8601 avec fuseau"
                      + " (2026-03-14T09:30:00Z). Autre format : 400 INVALID_DATE.",
              example = "2026-03-01")
          @RequestParam(required = false)
          String from,
      @Parameter(
              description =
                  "Fin de période sur la date de l'entrée, incluse : date AAAA-MM-JJ (jour entier"
                      + " compris) ou instant ISO 8601 avec fuseau. Ne doit pas être antérieure à"
                      + " from (la même journée est admise) : 400 RANGE_INVERTED sinon.",
              example = "2026-03-31")
          @RequestParam(required = false)
          String to,
      @Parameter(
              description = "Ne retenir que les entrées rattachées à cette séance (identifiant).",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @RequestParam(required = false)
          UUID sessionId,
      @Parameter(
              description =
                  "true : inclure aussi les entrées archivées (archivedAt renseigné) ; false"
                      + " (défaut) : les exclure.",
              example = "false")
          @RequestParam(defaultValue = "false")
          boolean includeArchived,
      @Parameter(
              description =
                  "Numéro de page, de 1 à 500 (défaut 1), 20 résultats par page ; hors bornes :"
                      + " 400 INVALID_PAGE.",
              schema =
                  @Schema(
                      type = "integer",
                      format = "int32",
                      minimum = "1",
                      maximum = "500",
                      defaultValue = "1"),
              example = "1")
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
