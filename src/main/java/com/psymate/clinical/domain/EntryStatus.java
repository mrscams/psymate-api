package com.psymate.clinical.domain;

/** Statut d'une entrée (cycle de vie A7) ; l'archivage est porté par archived_at. */
public enum EntryStatus {
  DRAFT("draft"),
  FINAL("final");

  private final String code;

  EntryStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static EntryStatus fromCode(String code) {
    return "draft".equals(code) ? DRAFT : FINAL;
  }
}
