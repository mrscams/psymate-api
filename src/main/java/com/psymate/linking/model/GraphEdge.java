package com.psymate.linking.model;

import java.util.UUID;

/**
 * Arête du graphe : un lien entre deux nœuds du graphe.
 *
 * @param linkType code du type (triggers, co_occurs…)
 * @param provenance manual en V1
 * @param status accepted dans un graphe (les autres statuts n'y figurent pas)
 */
public record GraphEdge(
    UUID id,
    UUID source,
    UUID target,
    String linkType,
    boolean directed,
    String provenance,
    String status) {}
