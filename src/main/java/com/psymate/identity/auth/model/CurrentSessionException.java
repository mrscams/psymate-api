package com.psymate.identity.auth.model;

/** Révocation de la session qui porte l'appel (M10-06) : 409, utiliser la déconnexion. */
public class CurrentSessionException extends RuntimeException {
  public CurrentSessionException() {
    super("current session");
  }
}
