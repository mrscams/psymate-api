package com.psymate.identity.admin.controller;

import com.psymate.identity.admin.dto.JournalDto;
import com.psymate.identity.admin.service.AdminJournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Journal d'administration (fiche 22, M1-14b) : lecture seule, aucune route d'écriture (405). Le
 * rôle admin et la 2FA sont contrôlés en amont par AdminTwoFactorFilter sur tout /api/admin/**.
 */
@Tag(
    name = "Administration : journal",
    description =
        "Journal d'audit en lecture seule des décisions et événements de sécurité de"
            + " l'administration (suspensions, décisions de dossier, recours, ouvertures de"
            + " justificatifs). Réservé aux administrateurs avec double authentification active.")
@RestController
@RequestMapping("/api/admin/journal")
public class AdminJournalController {

  private final AdminJournalService service;

  AdminJournalController(AdminJournalService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listAdminJournal",
      summary = "Liste le journal d'administration, du plus récent au plus ancien",
      description =
          """
          Administrateur avec 2FA. Lecture seule : décisions d'administration (kind admin.<kind>) \
          et événements de sécurité, du plus récent au plus ancien, avec filtres cumulables kind, \
          practitionerId, actorId, from, to. Jamais d'adresse IP, et les lignes d'audit patient \
          (kind patient.*) sont écartées. Pagination par curseur opaque : limit 50 par défaut, \
          bornée à 1..200 sans erreur ; nextCursor est null sur la dernière page. Aucune route \
          d'écriture sur le journal (405).""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Page du journal."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ :"
                + " INVALID_KIND (kind), INVALID_ID (practitionerId ou actorId, UUID illisible),"
                + " INVALID_DATE (from ou to), INVALID_CURSOR (cursor illisible).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  JournalDto list(
      @Parameter(
              description =
                  "Type d'événement : égalité exacte ou préfixe (kind=admin retient admin et"
                      + " tous les admin.*). Minuscules, chiffres, points et soulignés, 64"
                      + " caractères au plus, commençant par une lettre ; sinon 422 INVALID_KIND."
                      + " Absent : tous les types.",
              example = "admin.suspend")
          @RequestParam(required = false)
          String kind,
      @Parameter(
              description =
                  "Identifiant du compte concerné (UUID) ; sinon 422 INVALID_ID. Absent : tous.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @RequestParam(required = false)
          String practitionerId,
      @Parameter(
              description =
                  "Identifiant de l'administrateur auteur (UUID) ; sinon 422 INVALID_ID. Absent :"
                      + " tous.",
              example = "5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f")
          @RequestParam(required = false)
          String actorId,
      @Parameter(
              description =
                  "Début de période, inclus : date-heure ISO 8601 avec fuseau, ou jour AAAA-MM-JJ"
                      + " (début de journée UTC). Sinon 422 INVALID_DATE.",
              example = "2026-04-01")
          @RequestParam(required = false)
          String from,
      @Parameter(
              description =
                  "Fin de période, incluse : date-heure ISO 8601 avec fuseau, ou jour AAAA-MM-JJ"
                      + " (fin de journée UTC incluse). Sinon 422 INVALID_DATE.",
              example = "2026-04-30")
          @RequestParam(required = false)
          String to,
      @Parameter(
              description =
                  "Curseur opaque (nextCursor de la page précédente, à renvoyer tel quel) ;"
                      + " absent pour la première page. Illisible : 422 INVALID_CURSOR.")
          @RequestParam(required = false)
          String cursor,
      @Parameter(
              description =
                  "Nombre maximal de lignes par page : 50 par défaut, ramené dans l'intervalle"
                      + " 1 à 200 sans erreur.",
              schema = @Schema(type = "integer", format = "int32", defaultValue = "50"),
              example = "50")
          @RequestParam(required = false)
          Integer limit) {
    return service.list(kind, practitionerId, actorId, from, to, cursor, limit);
  }
}
