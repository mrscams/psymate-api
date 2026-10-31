package com.psymate.linking.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Lien accepté tel que les autres modules le lisent (graphe, vue d'ensemble, recherche).
 *
 * @param linkType code du type (triggers, co_occurs…)
 * @param provenance manual en V1
 * @param authorId praticien qui a posé le lien, nul pour une suggestion de l'IA (V2)
 */
public record LinkView(
    UUID id,
    UUID patientId,
    UUID sourceId,
    UUID targetId,
    String linkType,
    boolean directed,
    String provenance,
    String rationale,
    UUID authorId,
    Instant createdAt) {}
