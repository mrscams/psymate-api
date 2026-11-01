package com.psymate.clinical.api;

import java.util.UUID;

/**
 * Lien accepté vu par clinical (export, M6-05) : l'autre extrémité se déduit de sourceId/targetId.
 */
public record LinkedEntryView(UUID id, UUID sourceId, UUID targetId, String linkType) {}
