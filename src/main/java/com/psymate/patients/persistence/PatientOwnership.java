package com.psymate.patients.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projection JPQL de la garde de propriété : trois colonnes au lieu de la fiche entière (jsonb
 * compris), parce qu'elle est lue à chaque requête clinique.
 */
public record PatientOwnership(UUID tenantId, UUID ownerId, OffsetDateTime archivedAt) {}
