package com.psymate.clinical.api;

import java.util.List;

/**
 * Un lien demandé à la création d'une entrée est refusé : la création entière est annulée. {@code
 * index} est la position dans {@code links[]}, {@code field} le champ fautif de l'élément (linkType
 * ou targetId) et {@code code} le code d'erreur métier du lien (ENTRY_NOT_FOUND, SELF_LINK,
 * DIFFERENT_PATIENT, UNKNOWN_LINK_TYPE, LINK_TYPE_NOT_ALLOWED, LINK_EXISTS, ENTRY_ARCHIVED).
 */
public class EntryLinkException extends RuntimeException {

  private final int index;
  private final String field;
  private final String code;
  private final transient List<String> allowedLinkTypes;

  public EntryLinkException(int index, String field, String code, String message) {
    this(index, field, code, message, List.of());
  }

  public EntryLinkException(
      int index, String field, String code, String message, List<String> allowedLinkTypes) {
    super(message);
    this.index = index;
    this.field = field;
    this.code = code;
    this.allowedLinkTypes = List.copyOf(allowedLinkTypes);
  }

  public int index() {
    return index;
  }

  public String field() {
    return field;
  }

  public String code() {
    return code;
  }

  /** Codes des types possibles pour ce couple d'entrées (LINK_TYPE_NOT_ALLOWED), vide sinon. */
  public List<String> allowedLinkTypes() {
    return allowedLinkTypes;
  }
}
