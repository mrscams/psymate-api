package com.psymate.clinical.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Racine d'agrégat : l'entrée commune à tous les types (A4.1). {@code authorType} est porté en
 * lecture seule (A11) : le code V1 ne l'écrit jamais, la colonne vaut toujours 'practitioner'.
 */
public record Entry(
    UUID id,
    UUID tenantId,
    UUID patientId,
    EntryType type,
    EntryStatus status,
    Visibility visibility,
    TimeSpan time,
    UUID sessionId,
    UUID currentVersionId,
    int currentVersion,
    int linkCount,
    String authorType,
    Instant archivedAt,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt) {

  public boolean archived() {
    return archivedAt != null;
  }
}
