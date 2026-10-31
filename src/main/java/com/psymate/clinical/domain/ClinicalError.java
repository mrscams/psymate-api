package com.psymate.clinical.domain;

/** Codes d'erreur métier du module clinical, avec le statut HTTP que M1-05 leur associera. */
public enum ClinicalError {
  ENTRY_NOT_FOUND(404),
  ENTRY_ARCHIVED(409),
  UNAUTHENTICATED(401),
  SESSION_REQUIRED(422),
  SESSION_INVALID(422),
  SESSION_NOT_ALLOWED(422),
  SESSION_ALREADY_OPEN(409),
  SESSION_ALREADY_CLOSED(409),
  UNKNOWN_EMOTION(422),
  INVALID_TIME(422),
  NO_CURRENT_SESSION(404);

  private final int status;

  ClinicalError(int status) {
    this.status = status;
  }

  public int status() {
    return status;
  }
}
