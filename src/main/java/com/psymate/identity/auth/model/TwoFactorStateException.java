package com.psymate.identity.auth.model;

/** Opération 2FA incompatible avec l'état courant (déjà active, non initialisée). */
public class TwoFactorStateException extends RuntimeException {
  private final String code;

  public TwoFactorStateException(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
