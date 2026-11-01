package com.psymate.linking.web;

import com.psymate.linking.api.GraphQuery;
import com.psymate.linking.application.GraphQueryService;
import com.psymate.linking.web.GraphDtos.ExpandGraphRequest;
import com.psymate.linking.web.GraphDtos.GraphDto;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
   * liens suivis (liste séparée par des virgules ou paramètre répété) ; {@code from} et {@code to}
   * bornent occurred_from des nœuds (la racine est toujours rendue).
   */
  @GetMapping("/api/patients/{id}/graph")
  GraphDto getPatientGraph(
      @PathVariable UUID id,
      @RequestParam(required = false) UUID root,
      @RequestParam(defaultValue = "" + DEFAULT_DEPTH) int depth,
      @RequestParam(required = false) List<String> linkTypes,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return mapper.toDto(
        graph.neighborhood(new GraphQuery(id, root, depth, split(linkTypes), from, to)));
  }

  /**
   * Nœuds et arêtes d'un groupe : soit un super-nœud ({@code superNodeId}), soit une liste de
   * membres ({@code memberIds}), exactement l'un des deux.
   */
  @PostMapping("/api/patients/{id}/graph/expand")
  @ResponseStatus(HttpStatus.OK)
  GraphDto expandGraphGroup(@PathVariable UUID id, @RequestBody ExpandGraphRequest request) {
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
