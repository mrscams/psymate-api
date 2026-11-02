package com.psymate.linking.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Nœud du graphe : une entrée du patient.
 *
 * @param occurred début de l'occurrence (occurred_from de l'entrée)
 * @param intensity intensité 0..10, non nulle seulement pour un ressenti
 * @param emotion code d'émotion, non nul seulement pour un ressenti
 * @param degree nombre de liens acceptés incidents à l'entrée dans tout le dossier du patient (pas
 *     seulement dans le voisinage rendu)
 * @param distance distance à la racine : 0 pour la racine, 1 pour tous les nœuds d'une racine «
 *     patient »
 * @param authorType practitioner en V1 (A11) ; en lecture seule, jamais accepté en écriture
 */
public record GraphNode(
    UUID id,
    String type,
    String title,
    Instant occurred,
    Integer intensity,
    String emotion,
    int degree,
    int distance,
    String authorType) {}
