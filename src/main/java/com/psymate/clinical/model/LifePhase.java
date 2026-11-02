package com.psymate.clinical.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Phases de vie (A4.2), bornées en années depuis la date de naissance du patient. */
public enum LifePhase {
  CHILDHOOD("enfance", 0, 12);

  private final String labelFr;
  private final int fromYears;
  private final int toYears;

  LifePhase(String labelFr, int fromYears, int toYears) {
    this.labelFr = labelFr;
    this.fromYears = fromYears;
    this.toYears = toYears;
  }

  public int fromYears() {
    return fromYears;
  }

  public int toYears() {
    return toYears;
  }

  /** Reconnaît le libellé français ou le nom du code, sans tenir compte de casse ni d'accents. */
  public static Optional<LifePhase> fromLabel(String label) {
    if (label == null) {
      return Optional.empty();
    }
    String n = normalize(label);
    return Arrays.stream(values())
        .filter(p -> p.labelFr.equals(n) || p.name().toLowerCase(Locale.ROOT).equals(n))
        .findFirst();
  }

  private static String normalize(String s) {
    return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }
}
