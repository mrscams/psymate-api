package com.psymate.clinical.model;

/** Visibilité d'une entrée (A4.7) : privée au praticien, ou clinique (partagée avec l'équipe). */
public enum Visibility {
  PRIVATE("private"),
  CLINICAL("clinical");

  private final String code;

  Visibility(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static Visibility fromCode(String code) {
    return "private".equals(code) ? PRIVATE : CLINICAL;
  }
}
