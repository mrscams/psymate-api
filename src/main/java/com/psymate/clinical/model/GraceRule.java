package com.psymate.clinical.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Règle de grâce (A4.8) : une modification faite par le même auteur, moins de {@code period} après
 * la création, sur une entrée sans lien ni autre version, est appliquée en place, sans version et
 * sans événement. Une durée nulle ou négative désactive la règle. La durée est la propriété
 * versioning.grace-period, 75 minutes (1 h 15) par défaut (G, Q31).
 */
public record GraceRule(Duration period) {

  public boolean allows(Entry entry, UUID author, int versionCount, Instant now) {
    if (period == null || period.isZero() || period.isNegative()) {
      return false;
    }
    return author != null
        && author.equals(entry.createdBy())
        && now.isBefore(entry.createdAt().plus(period))
        && entry.linkCount() == 0
        && versionCount == 1;
  }
}
