package com.psymate.patients.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projection JPQL de l'identité de facturation (M15-04) : sept colonnes au lieu de la fiche
 * entière, sans {@code context} ni {@code follow_up}.
 */
public record PatientBillingIdentity(
    UUID id,
    UUID ownerId,
    String displayName,
    String firstName,
    String lastName,
    String email,
    OffsetDateTime archivedAt) {}
