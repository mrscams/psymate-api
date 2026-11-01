package com.psymate.linking.api;

import java.util.List;
import java.util.UUID;

/**
 * Regroupement de nœuds de degré 1 d'un même type reliés au même voisin (A6 « Graphe »), rendu à la
 * place des nœuds quand le voisinage dépasse la limite.
 *
 * @param id {@code sn:<neighborId>:<type>}, identifiant à rendre à {@code expand}
 * @param count nombre de nœuds regroupés
 * @param dominantEmotion code d'émotion le plus fréquent parmi les membres, nul si aucun membre n'a
 *     d'émotion
 * @param neighborId le nœud du graphe auquel le groupe est relié
 * @param memberIds identifiants des nœuds regroupés, triés
 */
public record GraphSuperNode(
    String id,
    String type,
    int count,
    String dominantEmotion,
    UUID neighborId,
    List<UUID> memberIds) {}
