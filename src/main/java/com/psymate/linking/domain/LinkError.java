package com.psymate.linking.domain;

/** Codes d'erreur métier du module linking, avec le statut HTTP associé. */
public enum LinkError {
  UNAUTHENTICATED(401),
  ENTRY_NOT_FOUND(404),
  LINK_NOT_FOUND(404),
  SELF_LINK(422),
  DIFFERENT_PATIENT(422),
  UNKNOWN_LINK_TYPE(422),
  LINK_TYPE_NOT_ALLOWED(422),
  LINK_EXISTS(409),
  LINK_NOT_ACTIVE(409),
  ENTRY_ARCHIVED(409);

  private final int status;

  LinkError(int status) {
    this.status = status;
  }

  public int status() {
    return status;
  }
}
