package com.psymate.linking.api;

import java.util.List;

/**
 * Voisinage d'une entrée (ou du patient) prêt à dessiner.
 *
 * @param truncated vrai si le voisinage a été réduit : super-nœud formé, nœuds retirés pour tenir
 *     dans la limite, ou racine « patient » limitée aux entrées les plus liées
 */
public record GraphView(
    List<GraphNode> nodes,
    List<GraphEdge> edges,
    List<GraphSuperNode> superNodes,
    boolean truncated) {}
