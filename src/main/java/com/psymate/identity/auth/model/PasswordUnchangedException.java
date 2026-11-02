package com.psymate.identity.auth.model;

/** Le nouveau mot de passe est identique à l'actuel. */
public class PasswordUnchangedException extends RuntimeException {
  public PasswordUnchangedException() {
    super("new password equals current password");
  }
}
