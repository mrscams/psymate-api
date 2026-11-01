package com.psymate.linking.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Demande de voisinage.
 *
 * @param rootId entrée racine ; nul = racine « patient » (les entrées les plus liées)
 * @param depth profondeur, de 1 à 3
 * @param linkTypes codes de types de liens à suivre et à rendre ; nul ou vide = tous
 * @param from borne basse (incluse) sur occurred_from des nœuds ; nulle = pas de borne ; la racine
 *     n'est jamais filtrée
 * @param to borne haute (incluse), idem
 */
public record GraphQuery(
    UUID patientId, UUID rootId, int depth, List<String> linkTypes, Instant from, Instant to) {}
