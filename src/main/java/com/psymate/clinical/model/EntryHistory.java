package com.psymate.clinical.model;

import java.util.List;
import java.util.UUID;

/**
 * Historique d'une entrée.
 *
 * @param authorType provenance de l'entrée (practitioner en V1, A11)
 * @param versions de la plus récente à la plus ancienne
 */
public record EntryHistory(UUID entryId, String authorType, List<EntryVersion> versions) {}
