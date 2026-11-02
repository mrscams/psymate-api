package com.psymate.linking.web;

import com.psymate.linking.application.GraphQuery;
import com.psymate.linking.application.GraphQueryService;
import com.psymate.linking.web.GraphDtos.ExpandGraphRequest;
import com.psymate.linking.web.GraphDtos.GraphDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Graphe d'un patient (A6, écran 4) : voisinage d'une entrée ou du patient, et développement d'un
 * super-nœud. Les paramètres sont validés par le service, après la garde de propriété : 422 avec
 * une entrée errors[] par champ.
 */
@Tag(
    name = "Graphe",
    description =
        "Graphe des entrées liées d'un patient : voisinage d'une entrée ou du dossier, et"
            + " développement d'un groupe de nœuds (super-nœud).")
@RestController
class GraphController {

  private static final int DEFAULT_DEPTH = 2;

  private final GraphQueryService graph;
  private final GraphMapper mapper;

  GraphController(GraphQueryService graph, GraphMapper mapper) {
    this.graph = graph;
    this.mapper = mapper;
  }

  /**
   * Voisinage de {@code root} à {@code depth} sauts (1 à 3, défaut 2), limité à 150 nœuds ; sans
   * {@code root}, les entrées les plus liées du patient. {@code linkTypes} limite les types de
   * liens suivis (liste séparée par des virgules ou paramètre répété) ; {@code from} (inclus) et
   * {@code to} (exclu) bornent occurred_from des nœuds (la racine est toujours rendue).
   */
  @GetMapping("/api/patients/{id}/graph")
  @Operation(
      summary =
          "Rend le graphe de liens autour d'une entrée ou, sans racine, du dossier du patient",
      description =
          """
          Session praticien propriétaire du patient. Seuls les liens acceptés et les entrées non \
          archivées du patient figurent dans le graphe. Avec root : voisinage de cette entrée à \
          depth sauts ; sans root : les entrées les plus liées du patient, toutes à distance 1. \
          La racine est toujours rendue, même hors de la période from/to. Au-delà de 150 nœuds, \
          les nœuds de degré 1 d'un même type reliés au même voisin sont regroupés en super-nœuds \
          (à développer par POST /api/patients/{id}/graph/expand), puis les nœuds les plus \
          éloignés sont retirés ; truncated vaut alors true. Réponse non paginée.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Graphe : nodes, edges, superNodes et truncated (nodes limité à 150)."),
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
            "PATIENT_NOT_FOUND : patient inexistant ; ENTRY_NOT_FOUND : la racine root n'existe"
                + " pas ou appartient à un patient d'un autre praticien.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "ENTRY_ARCHIVED : la racine root est archivée, errors[] porte le champ root.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message}, une entrée par paramètre"
                + " fautif ; codes de champ : OUT_OF_RANGE (depth hors de 1 à 3), INVALID_TIME"
                + " (from postérieur à to), INVALID_VALUE (linkTypes contient un code absent du"
                + " catalogue ou inactif) ; DIFFERENT_PATIENT (root : entrée d'un autre patient du"
                + " même praticien).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  GraphDto getPatientGraph(
      @Parameter(
              description = "Identifiant du patient.",
              example = "3f1c8e52-7a4d-4b9e-8c60-1d2e3f4a5b6c")
          @PathVariable
          UUID id,
      @Parameter(
              description =
                  "Entrée racine du voisinage. Absent : les entrées les plus liées du patient"
                      + " (150 au plus), toutes à distance 1.",
              example = "9b4c0c7e-3c1a-4f2e-9d6a-2a4d6f8b1c3e")
          @RequestParam(required = false)
          UUID root,
      @Parameter(
              description =
                  "Nombre de sauts depuis la racine, de 1 à 3, défaut 2 (422 OUT_OF_RANGE"
                      + " sinon, même sans root) ; ignoré pour le voisinage quand root est absent.",
              schema =
                  @Schema(
                      type = "integer",
                      format = "int32",
                      minimum = "1",
                      maximum = "3",
                      defaultValue = "2"),
              example = "2")
          @RequestParam(defaultValue = "" + DEFAULT_DEPTH)
          int depth,
      @Parameter(
              description =
                  "Codes de types de liens suivis, parmi ceux de GET /api/vocabulary/link-types :"
                      + " liste séparée par des virgules ou paramètre répété. Absent : tous les"
                      + " types.",
              example = "triggers,worsens")
          @RequestParam(required = false)
          List<String> linkTypes,
      @Parameter(
              description =
                  "Borne basse incluse sur la date de début (occurred) des nœuds, ISO 8601 UTC."
                      + " Absent : pas de borne.",
              example = "2026-01-01T00:00:00Z")
          @RequestParam(required = false)
          Instant from,
      @Parameter(
              description =
                  "Borne haute exclue sur la date de début (occurred) des nœuds, ISO 8601 UTC."
                      + " Absent : pas de borne.",
              example = "2026-07-01T00:00:00Z")
          @RequestParam(required = false)
          Instant to) {
    return mapper.toDto(
        graph.neighborhood(new GraphQuery(id, root, depth, split(linkTypes), from, to)));
  }

  /**
   * Nœuds et arêtes d'un groupe : soit un super-nœud ({@code superNodeId}), soit une liste de
   * membres ({@code memberIds}), exactement l'un des deux.
   */
  @PostMapping("/api/patients/{id}/graph/expand")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Développe un super-nœud ou une liste d'entrées du graphe",
      description =
          """
          Session praticien propriétaire du patient. Le corps contient exactement un de superNodeId \
          (identifiant d'un élément de superNodes d'un graphe déjà reçu) et memberIds (1 à 500 \
          identifiants d'entrées vivantes du patient, sans doublon). Rend les membres (à distance \
          1, avec leur degré dans tout le dossier) et les liens acceptés incidents à au moins l'un \
          d'eux ; superNodes est toujours vide. Un super-nœud se développe sans les filtres du \
          graphe d'origine et au plus 500 membres sont rendus : truncated vaut alors true. \
          Réponse non paginée.""")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Membres du groupe et arêtes incidentes (superNodes vide)."),
    @ApiResponse(
        responseCode = "400",
        description = "MALFORMED_REQUEST : corps JSON absent ou illisible sans champ identifiable.",
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
        description =
            "PATIENT_NOT_FOUND : patient inexistant ; SUPER_NODE_NOT_FOUND : le groupe superNodeId"
                + " n'existe plus (entrée pivot absente ou archivée, aucun membre vivant du type).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ :"
                + " INVALID_VALUE (superNodeId : ni superNodeId ni memberIds, ou les deux ;"
                + " memberIds : doublon ou élément nul), INVALID_FORMAT (superNodeId qui n'est pas"
                + " de la forme sn:{uuid}:{type d'entrée} ; memberIds contenant un non-UUID),"
                + " REQUIRED (memberIds vide), OUT_OF_RANGE (memberIds de plus de 500 éléments),"
                + " ENTRY_NOT_FOUND (memberIds : une entrée est inexistante, archivée ou d'un autre"
                + " patient).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  GraphDto expandGraphGroup(
      @Parameter(
              description = "Identifiant du patient.",
              example = "3f1c8e52-7a4d-4b9e-8c60-1d2e3f4a5b6c")
          @PathVariable
          UUID id,
      @RequestBody ExpandGraphRequest request) {
    return mapper.toDto(graph.expand(id, request.superNodeId(), request.memberIds()));
  }

  /** Liste séparée par des virgules ou répétée : un code par élément, sans blancs. */
  private static List<String> split(List<String> raw) {
    if (raw == null) {
      return List.of();
    }
    return raw.stream()
        .flatMap(v -> Arrays.stream(v.split(",")))
        .map(String::strip)
        .filter(v -> !v.isEmpty())
        .toList();
  }
}
