package com.psymate.patients.controller;

import com.psymate.patients.dto.CreatePatientProfileItemRequest;
import com.psymate.patients.dto.PatientProfileDto;
import com.psymate.patients.dto.PatientProfileItemDto;
import com.psymate.patients.dto.UpdatePatientProfileItemRequest;
import com.psymate.patients.service.PatientProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Profil 360 d'un patient (G Q62 à Q70, J3) : quatrième onglet du dossier. */
@Tag(
    name = "Profil 360",
    description =
        "Ce qui décrit la personne elle-même, hors séance, rangé en quatre rubriques (history,"
            + " family, health, values_goals) : lecture de tout le profil, ajout, correction sur"
            + " place et retrait d'une information. Une information n'appartient à aucune séance"
            + " et n'a pas d'historique de versions.")
@RestController
@RequestMapping("/api/patients/{id}/profile-items")
class PatientProfileController {

  private static final String PROBLEM = "application/problem+json";

  private final PatientProfileService profile;

  PatientProfileController(PatientProfileService profile) {
    this.profile = profile;
  }

  @GetMapping
  @Operation(
      summary = "Lit tout le Profil 360 d'un patient",
      description =
          """
          Session praticien propriétaire du patient ; fonctionne aussi pour un patient archivé. \
          Renvoie toutes les informations actives d'un coup, sans pagination (500 au plus par \
          patient) ; une information retirée n'est plus renvoyée. Tri : rubrique dans l'ordre \
          history, family, health, values_goals ; dans history, periodYear croissant (les \
          informations sans année en dernier) puis createdAt ; ailleurs, createdAt croissant.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Profil du patient (liste éventuellement vide)."),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien (ou d'un autre cabinet).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "PATIENT_NOT_FOUND : aucun patient avec cet identifiant.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  PatientProfileDto listPatientProfileItems(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id) {
    return profile.profile(id);
  }

  @PostMapping
  @Operation(
      summary = "Ajoute une information au Profil 360",
      description =
          """
          Session praticien propriétaire du patient, patient actif (409 PATIENT_ARCHIVED sinon). \
          L'information n'appartient à aucune séance et ne crée aucune entrée. La période \
          (periodLabel, periodYear) n'est admise que dans history. Un patient compte 500 \
          informations actives au plus (422 PROFILE_ITEM_LIMIT_REACHED au-delà). Réponse 201 \
          avec l'information créée.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description =
            "Information créée ; en-tête Location = /api/patients/{id}/profile-items/{itemId}.",
        headers =
            @Header(
                name = "Location",
                description =
                    "URL de l'information créée, /api/patients/{id}/profile-items/{itemId}.",
                schema = @Schema(type = "string"))),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_JSON : corps absent ou JSON illisible.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "PATIENT_NOT_FOUND : aucun patient avec cet identifiant.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "PATIENT_ARCHIVED : patient archivé, restaurer d'abord.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "PROFILE_ITEM_LIMIT_REACHED : le patient a déjà 500 informations actives ;"
                + " VALIDATION_FAILED avec errors[] {field, code, message} : category REQUIRED"
                + " (absente) ou INVALID_VALUE (hors history, family, health, values_goals) ;"
                + " title REQUIRED (absent ou vide) ou OUT_OF_RANGE (plus de 200 caractères) ;"
                + " detail OUT_OF_RANGE (plus de 2 000 caractères) ; periodLabel OUT_OF_RANGE"
                + " (plus de 60 caractères) ; periodYear OUT_OF_RANGE (hors 1900 à 2100) ;"
                + " periodLabel ou periodYear INVALID_VALUE (rubrique autre que history) ;"
                + " INVALID_FORMAT (valeur illisible, periodYear non entier) ; UNKNOWN_FIELD (clé"
                + " inconnue).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<PatientProfileItemDto> createPatientProfileItem(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @RequestBody CreatePatientProfileItemRequest request) {
    PatientProfileItemDto created = profile.create(id, request);
    return ResponseEntity.created(
            URI.create("/api/patients/" + id + "/profile-items/" + created.id()))
        .body(created);
  }

  @PatchMapping("/{itemId}")
  @Operation(
      summary = "Corrige une information du Profil 360",
      description =
          """
          Session praticien propriétaire du patient, patient actif (409 PATIENT_ARCHIVED sinon). \
          Correction partielle et sur place, sans version : une clé absente reste inchangée ; \
          null efface detail, periodLabel ou periodYear ; title ne peut pas devenir vide. La \
          rubrique peut changer : en quittant history, la période est effacée par le serveur. \
          updatedAt avance si un champ change. Une information retirée ne se corrige plus \
          (409 PROFILE_ITEM_ARCHIVED).""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Information corrigée."),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_JSON : corps absent ou JSON illisible.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "PATIENT_NOT_FOUND : aucun patient avec cet identifiant ; PROFILE_ITEM_NOT_FOUND :"
                + " aucune information de ce patient avec cet identifiant.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "PATIENT_ARCHIVED : patient archivé ; PROFILE_ITEM_ARCHIVED : information retirée du"
                + " profil.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} : category REQUIRED (null)"
                + " ou INVALID_VALUE (hors history, family, health, values_goals) ; title"
                + " REQUIRED (null ou vide) ou OUT_OF_RANGE (plus de 200 caractères) ; detail"
                + " OUT_OF_RANGE (plus de 2 000 caractères) ; periodLabel OUT_OF_RANGE (plus de 60"
                + " caractères) ; periodYear OUT_OF_RANGE (hors 1900 à 2100) ; periodLabel ou"
                + " periodYear INVALID_VALUE (non null avec une rubrique finale autre que"
                + " history) ; INVALID_FORMAT (valeur illisible) ; UNKNOWN_FIELD (clé inconnue).",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  PatientProfileItemDto updatePatientProfileItem(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @Parameter(
              description = "Identifiant de l'information.",
              example = "3f1c2a9e-8b7d-4c6e-a5f4-1e2d3c4b5a69")
          @PathVariable
          UUID itemId,
      @RequestBody UpdatePatientProfileItemRequest request) {
    return profile.correct(id, itemId, request);
  }

  @DeleteMapping("/{itemId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Retire une information du Profil 360",
      description =
          """
          Session praticien propriétaire du patient, patient actif (409 PATIENT_ARCHIVED sinon). \
          Archivage logique : rien n'est supprimé, l'information n'est plus renvoyée par la \
          lecture et ne se corrige plus. Idempotent : retirer une information déjà retirée \
          renvoie aussi 204. Aucune restauration n'existe.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "204",
        description = "Information retirée (ou déjà retirée) ; pas de corps."),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "PATIENT_NOT_FOUND : aucun patient avec cet identifiant ; PROFILE_ITEM_NOT_FOUND :"
                + " aucune information de ce patient avec cet identifiant.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "PATIENT_ARCHIVED : patient archivé, restaurer d'abord.",
        content =
            @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
  })
  void archivePatientProfileItem(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @Parameter(
              description = "Identifiant de l'information.",
              example = "3f1c2a9e-8b7d-4c6e-a5f4-1e2d3c4b5a69")
          @PathVariable
          UUID itemId) {
    profile.archive(id, itemId);
  }
}
