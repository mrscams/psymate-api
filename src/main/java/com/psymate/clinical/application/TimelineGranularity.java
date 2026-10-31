package com.psymate.clinical.application;

import java.util.Arrays;
import java.util.Optional;

/** Granularité du résumé de timeline : le code est aussi l'unité de date_trunc. */
public enum TimelineGranularity {
  MONTH("month"),
  YEAR("year");

  private final String code;

  TimelineGranularity(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static Optional<TimelineGranularity> fromCode(String code) {
    return Arrays.stream(values()).filter(g -> g.code.equals(code)).findFirst();
  }
}
