package com.psymate.identity.auth.model;

import java.util.List;

/** Erreurs de validation par champ détectées par le service (adresse, téléphone). */
public class FieldValidationException extends RuntimeException {
  private final List<String[]> errors;

  /** Chaque entrée : { champ, code, clé de message }. */
  public FieldValidationException(List<String[]> errors) {
    super("invalid fields");
    this.errors = errors;
  }

  public List<String[]> errors() {
    return errors;
  }
}
