package com.psymate.patients.controller;

import com.psymate.patients.dto.CreatePatientRequest;
import com.psymate.patients.dto.FlagRequest;
import com.psymate.patients.dto.PatientDto;
import com.psymate.patients.dto.PatientPage;
import com.psymate.patients.dto.TagsRequest;
import com.psymate.patients.dto.VersionDto;
import com.psymate.patients.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Patients du praticien courant (fiches 01 et 16). */
@Tag(
    name = "Patients",
    description =
        "Fiches des patients du praticien : liste, création, modification versionnée, archivage,"
            + " drapeaux et tags.")
@RestController
@RequestMapping("/api/patients")
class PatientController {

  private final PatientService patients;

  PatientController(PatientService patients) {
    this.patients = patients;
  }

  @GetMapping
  @Operation(
      summary = "Liste les patients du praticien connecté",
      description =
          """
          Session praticien ; ne renvoie que les patients de son cabinet. Par défaut les patients \
          actifs ; archived=true renvoie uniquement les patients archivés. Le filtre q porte sur le \
          nom affiché (insensible à la casse et aux accents), tag filtre sur un tag du praticien \
          (un tag inconnu donne une liste vide). Pagination par curseur opaque : limit 50 par \
          défaut, ramené à 1..200 ; nextCursor est null sur la dernière page.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Page de patients."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : paramètre invalide, errors[] {field, code, message} avec field ="
                + " sort (INVALID_VALUE : valeur autre que name, created, updated) ou cursor"
                + " (INVALID_FORMAT : curseur illisible).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  PatientPage listPatients(
      @Parameter(
              description =
                  "Filtre « contient » sur le nom affiché, insensible à la casse et aux accents ;"
                      + " les espaces multiples sont réduits. Vide ou absent : pas de filtre.",
              example = "eloi")
          @RequestParam(required = false)
          String q,
      @Parameter(
              description =
                  "false (défaut) : patients actifs seulement ; true : patients archivés"
                      + " seulement.",
              example = "false")
          @RequestParam(defaultValue = "false")
          boolean archived,
      @Parameter(
              description =
                  "Libellé d'un tag du praticien ; ne renvoie que les patients qui le portent. Un"
                      + " libellé qui ne correspond à aucun tag donne une liste vide.",
              example = "sommeil")
          @RequestParam(required = false)
          String tag,
      @Parameter(
              description =
                  "Ordre de tri : name (défaut, alphabétique sans accent ni casse), created"
                      + " (création décroissante) ou updated (dernière modification décroissante).",
              schema = @Schema(allowableValues = {"name", "created", "updated"}),
              example = "name")
          @RequestParam(required = false)
          String sort,
      @Parameter(
              description =
                  "Curseur opaque lu dans nextCursor de la page précédente ; absent pour la"
                      + " première page.",
              example = "bzo1MA")
          @RequestParam(required = false)
          String cursor,
      @Parameter(
              description =
                  "Nombre maximal de patients par page : 50 par défaut ; une valeur hors de"
                      + " 1..200 est ramenée à la borne.",
              schema =
                  @Schema(
                      type = "integer",
                      format = "int32",
                      minimum = "1",
                      maximum = "200",
                      defaultValue = "50"),
              example = "50")
          @RequestParam(required = false)
          Integer limit) {
    return patients.list(q, archived, tag, sort, cursor, limit);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Crée un patient dans le cabinet du praticien",
      description =
          """
          Session praticien ; le patient appartient au praticien connecté. Seul displayName est \
          obligatoire. Des drapeaux (un par nature) et des tags existants peuvent être posés dès la \
          création ; la fiche démarre à la version 1. La création est atomique : en cas d'erreur \
          rien n'est enregistré. Réponse 201 avec la fiche complète.""")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Patient créé ; corps = fiche complète."),
    @ApiResponse(
        responseCode = "409",
        description = "FLAG_ALREADY_ACTIVE : la même nature de drapeau est présente deux fois.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : errors[] {field, code, message} avec field = displayName"
                + " (REQUIRED si absent ou vide, TOO_LONG au-delà de 120 caractères), firstName et"
                + " lastName (TOO_LONG au-delà de 120 caractères), birthDate (INVALID_DATE : future"
                + " ou de plus de 120 ans), pronoun (INVALID_VALUE : hors elle, il, iel, neutre),"
                + " flags[i].kind (REQUIRED si absente, INVALID_VALUE si inconnue),"
                + " flags[i].reason (REQUIRED pour watch), tags[i] (REQUIRED si libellé vide) ;"
                + " TAG_IS_EMOTION : un libellé de tags correspond à une émotion (même code dans"
                + " errors[0]) ; TAG_UNKNOWN : un libellé de tags n'existe pas encore (créer le tag"
                + " d'abord ; même code dans errors[0]).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  PatientDto createPatient(@RequestBody CreatePatientRequest request) {
    return patients.create(request);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Lit la fiche d'un patient",
      description =
          """
          Session praticien propriétaire du patient. Fonctionne aussi pour un patient archivé \
          (archivedAt renseigné). Renvoie la fiche avec ses drapeaux actifs et ses tags.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Fiche du patient."),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien (ou d'un autre cabinet).",
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
  PatientDto getPatient(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id) {
    return patients.get(id);
  }

  @PatchMapping("/{id}")
  @Operation(
      summary = "Modifie la fiche d'un patient en créant une version",
      description =
          """
          Session praticien propriétaire du patient, patient actif (409 PATIENT_ARCHIVED sinon). \
          Mise à jour partielle : seules les clés présentes du corps sont modifiées ; une valeur \
          null ou vide efface un champ texte (sauf displayName, obligatoire : 422) ; birthDate \
          s'efface avec null (une chaîne vide est refusée en 422). context et followUp sont \
          fusionnés clé à clé (une clé \
          à null est retirée ; l'objet entier à null le vide). Chaque appel réussi incrémente \
          currentVersion et archive un instantané (GET /versions) ; reason, facultatif, est le \
          motif conservé dans la version.""")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description =
          "Objet JSON partiel. Clés admises : displayName, firstName, lastName, birthDate"
              + " (AAAA-MM-JJ), pronoun, phone, email, context (objet), followUp (objet), reason"
              + " (motif de la modification, non stocké sur la fiche). Toute autre clé (drapeaux,"
              + " tags, archivedAt, authorType…) est refusée en 422 VALIDATION_FAILED.",
      required = true)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Fiche modifiée ; corps = fiche complète."),
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
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "PATIENT_ARCHIVED : patient archivé, restaurer d'abord.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : errors[] {field, code, message} avec field = nom de la clé"
                + " inconnue ou non modifiable (UNKNOWN_FIELD), displayName (REQUIRED si vide,"
                + " TOO_LONG au-delà de 120 caractères), firstName et lastName (TOO_LONG), pronoun"
                + " (INVALID_VALUE : hors elle, il, iel, neutre), birthDate (INVALID_FORMAT si"
                + " illisible, INVALID_DATE si future ou de plus de 120 ans), context ou followUp"
                + " (INVALID_FORMAT : objet attendu).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  PatientDto patchPatient(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @RequestBody Map<String, Object> body) {
    return patients.patch(id, body);
  }

  @GetMapping("/{id}/versions")
  @Operation(
      summary = "Liste l'historique des versions de la fiche d'un patient",
      description =
          """
          Session praticien propriétaire du patient ; fonctionne aussi pour un patient archivé. \
          Une entrée par modification (PATCH), de la plus ancienne à la plus récente, avec \
          l'instantané de la fiche après la modification ; la création (version 1) n'en a pas. \
          Liste non paginée, vide tant que la fiche n'a jamais été modifiée.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Versions, de la plus ancienne à la plus récente."),
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
  List<VersionDto> listPatientVersions(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id) {
    return patients.versions(id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Archive un patient",
      description =
          """
          Session praticien propriétaire du patient. Archivage logique : rien n'est supprimé, le \
          patient disparaît de la liste par défaut (visible avec archived=true) et ses écritures \
          sont refusées jusqu'à la restauration (POST /restore). Idempotent : archiver un patient \
          déjà archivé renvoie aussi 204.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "204",
        description = "Patient archivé (ou déjà archivé) ; pas de corps."),
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
  void archivePatient(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id) {
    patients.archive(id);
  }

  @PostMapping("/{id}/restore")
  @Operation(
      summary = "Restaure un patient archivé",
      description =
          """
          Session praticien propriétaire du patient. Lève l'archivage : le patient réapparaît dans \
          la liste par défaut et accepte de nouveau les écritures. Contrairement aux autres \
          écritures, la restauration est acceptée sur un patient archivé. Idempotent : sur un \
          patient actif, renvoie la fiche inchangée en 200. Aucun corps de requête.""")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Patient restauré ; corps = fiche complète."),
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
  PatientDto restorePatient(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id) {
    return patients.restore(id);
  }

  @PostMapping("/{id}/flags")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Pose un drapeau sur un patient",
      description =
          """
          Session praticien propriétaire du patient, patient actif (409 PATIENT_ARCHIVED sinon). \
          Un seul drapeau actif par nature (watch, risk, shared_care) : le reposer est refusé tant \
          qu'il n'a pas été levé. watch exige une raison. Réponse 201 avec la fiche complète du \
          patient (et non le drapeau seul).""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Drapeau posé ; corps = fiche complète du patient."),
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
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description =
            "PATIENT_ARCHIVED : patient archivé ; FLAG_ALREADY_ACTIVE : ce drapeau est déjà actif.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : errors[] {field, code, message} avec field = flag.kind (REQUIRED"
                + " si absente, INVALID_VALUE si inconnue) ou flag.reason (REQUIRED pour watch).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  PatientDto addPatientFlag(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @RequestBody FlagRequest request) {
    return patients.addFlag(id, request);
  }

  @DeleteMapping("/{id}/flags/{kind}")
  @Operation(
      summary = "Lève un drapeau actif d'un patient",
      description =
          """
          Session praticien propriétaire du patient, patient actif (409 PATIENT_ARCHIVED sinon). \
          Le drapeau levé n'apparaît plus dans flags ; il peut être reposé ensuite. Réponse 200 avec \
          la fiche complète du patient.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Drapeau levé ; corps = fiche complète du patient."),
    @ApiResponse(
        responseCode = "403",
        description = "PATIENT_FORBIDDEN : patient d'un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description =
            "PATIENT_NOT_FOUND : aucun patient avec cet identifiant ; FLAG_NOT_ACTIVE : ce"
                + " drapeau n'est pas actif sur le patient.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "PATIENT_ARCHIVED : patient archivé, restaurer d'abord.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : errors[] {field, code, message} avec field = kind (INVALID_VALUE :"
                + " nature de drapeau inconnue).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  PatientDto clearPatientFlag(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @Parameter(
              description =
                  "Nature du drapeau à lever : watch (à surveiller), risk (risque) ou shared_care"
                      + " (suivi partagé).",
              schema = @Schema(allowableValues = {"watch", "risk", "shared_care"}),
              example = "watch")
          @PathVariable
          String kind) {
    return patients.clearFlag(id, kind);
  }

  @PutMapping("/{id}/tags")
  @Operation(
      summary = "Remplace la liste des tags d'un patient",
      description =
          """
          Session praticien propriétaire du patient, patient actif (409 PATIENT_ARCHIVED sinon). \
          Remplacement complet : les tags absents du corps sont retirés du patient (liste vide = \
          aucun tag). Les tags doivent exister (POST /api/tags) et ne pas être des émotions. \
          Réponse 200 avec la fiche complète du patient.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Tags remplacés ; corps = fiche complète du patient."),
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
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "PATIENT_ARCHIVED : patient archivé, restaurer d'abord.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED : libellé vide, errors[] {field, code, message} avec field = tags[i]"
                + " et code REQUIRED ; TAG_IS_EMOTION : le libellé correspond à une émotion"
                + " (utiliser l'émotion) ; TAG_UNKNOWN : le tag n'existe pas (le créer d'abord par"
                + " POST /api/tags) ; errors[0] porte alors le même code.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  PatientDto replacePatientTags(
      @Parameter(
              description = "Identifiant du patient.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @PathVariable
          UUID id,
      @RequestBody TagsRequest request) {
    return patients.replaceTags(id, request.tags());
  }
}
