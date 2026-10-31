package com.psymate.identity.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement publié quand un praticien s'inscrit (compte créé, e-mail non confirmé). Ne contient
 * aucune donnée personnelle : seulement des identifiants et la profession.
 */
public record PractitionerRegistered(
    UUID eventId, Instant occurredAt, UUID practitionerId, UUID tenantId, String profession) {}
