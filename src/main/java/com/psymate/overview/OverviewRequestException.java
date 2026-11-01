package com.psymate.overview;

/** Paramètre de la vue d'ensemble invalide (400, OVERVIEW_BAD_REQUEST). */
class OverviewRequestException extends RuntimeException {

  private final String field;
  private final String code;

  OverviewRequestException(String field, String code) {
    super("Paramètre invalide : " + field + " (" + code + ")");
    this.field = field;
    this.code = code;
  }

  String field() {
    return field;
  }

  /** Clé overview.error.CODE des messages. */
  String code() {
    return code;
  }
}
