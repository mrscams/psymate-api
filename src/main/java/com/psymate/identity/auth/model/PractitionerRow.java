package com.psymate.identity.auth.model;

import java.util.Map;
import java.util.UUID;

public record PractitionerRow(
    UUID id,
    UUID tenantId,
    String email,
    String passwordHash,
    String firstName,
    String lastName,
    String displayName,
    String locale,
    String profession,
    String role,
    String verificationStatus,
    Map<String, Object> preferences) {

  /** Statut d'un compte dont l'adresse e-mail n'est pas confirmée (fiche 25). */
  public static final String EMAIL_PENDING = "email_pending";
}
