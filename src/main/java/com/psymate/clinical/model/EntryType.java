package com.psymate.clinical.model;

import java.util.Arrays;
import java.util.Optional;

/** Les six types d'entrées de V1 (E5.1) et la table de détail de chacun. */
public enum EntryType {
  NOTE("note", null),
  EVENT("event", null),
  SITUATION("situation", null),
  FEELING("feeling", "entry_feeling"),
  SESSION("session", "entry_session"),
  MEDICATION("medication", "entry_medication");

  private final String code;
  private final String detailTable;

  EntryType(String code, String detailTable) {
    this.code = code;
    this.detailTable = detailTable;
  }

  public String code() {
    return code;
  }

  /** Table de détail, ou {@code null} pour les types sans détail (note, event, situation). */
  public String detailTable() {
    return detailTable;
  }

  public static Optional<EntryType> fromCode(String code) {
    return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
  }
}
