package com.psymate.linking.domain;

import java.util.Arrays;

/**
 * Statut d'un lien (A7 « Cycle de vie d'un lien », E3.7). En V1 un lien naît {@code accepted} et
 * finit {@code superseded} ; {@code proposed} et {@code rejected} sont réservés aux suggestions de
 * l'IA (V2).
 */
public enum LinkStatus {
  PROPOSED("proposed"),
  ACCEPTED("accepted"),
  REJECTED("rejected"),
  SUPERSEDED("superseded");

  private final String code;

  LinkStatus(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** Vivant : seuls rejected et superseded sortent de l'index unique ux_link_active. */
  public boolean active() {
    return this == PROPOSED || this == ACCEPTED;
  }

  /** Seuls les liens acceptés comptent dans entry.link_count. */
  public boolean countsInLinkCount() {
    return this == ACCEPTED;
  }

  public static LinkStatus fromCode(String code) {
    return Arrays.stream(values())
        .filter(s -> s.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Statut de lien inconnu : " + code));
  }
}
