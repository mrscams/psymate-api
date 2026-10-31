package com.psymate.clinical.domain;

/** Erreur métier du module clinical (hors erreurs de validation Jakarta). */
public class ClinicalException extends RuntimeException {

  private final ClinicalError error;

  public ClinicalException(ClinicalError error, String message) {
    super(message);
    this.error = error;
  }

  public ClinicalError error() {
    return error;
  }
}
