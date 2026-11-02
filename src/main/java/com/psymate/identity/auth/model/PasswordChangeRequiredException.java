package com.psymate.identity.auth.model;

/** Compte de bootstrap (M7-08) : le mot de passe à usage unique doit d'abord être remplacé. */
public class PasswordChangeRequiredException extends RuntimeException {
  public PasswordChangeRequiredException() {
    super("password change required");
  }
}
