package com.psymate.directory;

import com.psymate.directory.DirectoryVocabularyAdminService.CreateRequest;
import com.psymate.directory.DirectoryVocabularyAdminService.ItemDto;
import com.psymate.directory.DirectoryVocabularyAdminService.ListDto;
import com.psymate.directory.DirectoryVocabularyAdminService.PatchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration du vocabulaire de l'annuaire (G Q15, fiche 21, M1-14b). Vit dans directory et non
 * dans identity.admin : identity ne peut pas dépendre de directory (cycle). Le rôle admin et la 2FA
 * sont contrôlés en amont par AdminTwoFactorFilter sur tout /api/admin/**. Aucune route DELETE.
 */
@Tag(
    name = "Administration : vocabulaire de l'annuaire",
    description =
        "Gestion par l'administrateur des spécialités et des publics proposés dans l'annuaire :"
            + " ajout, renommage, ordre et activation (jamais de suppression).")
@RestController
@RequestMapping("/api/admin/vocabulary/directory")
class DirectoryVocabularyAdminController {

  private final DirectoryVocabularyAdminService service;

  DirectoryVocabularyAdminController(DirectoryVocabularyAdminService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listAdminDirectoryVocabulary",
      summary =
          "Spécialités et publics de l'annuaire, actifs ou non, avec le nombre de fiches publiées"
              + " qui les utilisent",
      description =
          """
          Admin avec double authentification active. Rend toutes les entrées, actives ou non, des \
          deux types (par type puis rang puis libellé français) ou d'un seul avec kind ; \
          usedByPublishedProfiles compte les fiches publiées qui utilisent le code. Liste \
          complète, non paginée.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Entrées du vocabulaire (items)."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ :"
                + " INVALID_VALUE (kind autre que specialty ou audience).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ListDto list(
      @Parameter(
              description =
                  "Type à lister : specialty (spécialités) ou audience (publics). Absent ou vide :"
                      + " les deux types.",
              schema = @Schema(allowableValues = {"specialty", "audience"}),
              example = "specialty")
          @RequestParam(required = false)
          String kind) {
    return service.list(kind);
  }

  @PostMapping
  @Operation(
      operationId = "createAdminDirectoryVocabulary",
      summary = "Ajoute une spécialité ou un public (409 VOCABULARY_EXISTS si le code existe)",
      description =
          """
          Admin avec double authentification active. Crée une entrée active ; le code ne change \
          plus ensuite et ne se supprime jamais. Sans sortOrder, l'entrée prend le rang suivant \
          celui du dernier de son type (32767 au plus). Réponse 201 avec l'entrée créée.""")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Entrée créée."),
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_BODY : corps JSON illisible (syntaxe ou type de champ invalide).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "VOCABULARY_EXISTS : ce code existe déjà pour ce type.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ :"
                + " INVALID_VALUE (kind absent ou autre que specialty ou audience), REQUIRED"
                + " (code, labelFr), INVALID_FORMAT (code hors du format minuscule, chiffres et"
                + " underscores, 2 à 40 caractères, commençant par une lettre), TOO_LONG (labelFr,"
                + " labelEn au-delà de 120 caractères), OUT_OF_RANGE (sortOrder hors de 0 à"
                + " 32767).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<ItemDto> create(@RequestBody CreateRequest body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
  }

  @PatchMapping("/{kind}/{code}")
  @Operation(
      operationId = "updateAdminDirectoryVocabulary",
      summary =
          "Modifie libellés, rang ou activation (409 VOCABULARY_IN_USE pour désactiver un code"
              + " utilisé par une fiche publiée)",
      description =
          """
          Admin avec double authentification active. Modification partielle : un champ absent reste \
          inchangé, labelEn vide efface le libellé anglais. Une entrée désactivée disparaît de \
          GET /api/vocabulary/directory et ne peut plus être choisie ni publiée ; on ne peut la \
          désactiver que si aucune fiche publiée ne l'utilise. Le code et le type ne changent \
          pas.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Entrée mise à jour."),
    @ApiResponse(
        responseCode = "400",
        description = "INVALID_BODY : corps JSON illisible (syntaxe ou type de champ invalide).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "VOCABULARY_NOT_FOUND : type inconnu ou code inexistant pour ce type.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "VOCABULARY_IN_USE : désactivation refusée, au moins une fiche publiée utilise ce"
                + " code ; le champ usedByPublishedProfiles donne leur nombre.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ : REQUIRED"
                + " (labelFr vide), TOO_LONG (labelFr, labelEn au-delà de 120 caractères),"
                + " OUT_OF_RANGE (sortOrder hors de 0 à 32767).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ItemDto update(
      @Parameter(
              description = "Type de l'entrée : specialty (spécialité) ou audience (public).",
              schema = @Schema(allowableValues = {"specialty", "audience"}),
              example = "specialty")
          @PathVariable
          String kind,
      @Parameter(description = "Code de l'entrée à modifier.", example = "anxiety") @PathVariable
          String code,
      @RequestBody PatchRequest body) {
    return service.update(kind, code, body);
  }
}
