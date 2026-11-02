package com.psymate.identity.auth.model;

/** Code TOTP ou mot de passe faux lors de l'activation ou de la désactivation de la 2FA. */
public class InvalidSecondFactorException extends RuntimeException {
  private final String field;

  public InvalidSecondFactorException(String field) {
    super("invalid " + field);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
