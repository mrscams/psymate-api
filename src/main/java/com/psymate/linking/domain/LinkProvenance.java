package com.psymate.linking.domain;

import java.util.Arrays;

/** Origine d'un lien (E3.7). En V1 : toujours {@code manual}. */
public enum LinkProvenance {
  MANUAL("manual"),
  AI_SUGGESTED("ai_suggested"),
  AI_CONFIRMED("ai_confirmed");

  private final String code;

  LinkProvenance(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static LinkProvenance fromCode(String code) {
    return Arrays.stream(values())
        .filter(p -> p.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Provenance de lien inconnue : " + code));
  }
}
