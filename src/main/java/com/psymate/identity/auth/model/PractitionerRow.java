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

  /** Préférence posée par le compte admin de bootstrap (M7-08) : mot de passe à changer. */
  public static final String MUST_CHANGE_PASSWORD = "security.mustChangePassword";

  /** Vrai tant que le mot de passe à usage unique d'un compte de bootstrap n'a pas été changé. */
  public boolean mustChangePassword() {
    return mustChangePassword(preferences);
  }

  /** Même lecture du drapeau à partir des seules préférences (projection de GET /api/auth/me). */
  public static boolean mustChangePassword(Map<String, Object> preferences) {
    return preferences != null
        && preferences.get(MUST_CHANGE_PASSWORD) instanceof Boolean flag
        && flag;
  }
}
