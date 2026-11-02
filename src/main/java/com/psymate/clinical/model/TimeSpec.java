package com.psymate.clinical.model;

import java.time.Instant;

/**
 * Saisie du temps d'occurrence (A4.2), avant résolution en {@link TimeSpan}.
 *
 * @param precision précision déclarée
 * @param at instant de référence : l'instant exact, un instant quelconque du jour, du mois ou de
 *     l'année visés, ou le début d'une période ; ignoré pour life_phase
 * @param to fin d'une période (obligatoire pour period sauf si ongoing), ou fin d'un exact
 * @param label libellé d'origine (« été 2019 », « enfance »), obligatoire pour period et life_phase
 * @param ongoing vrai pour « depuis… » : occurred_to reste NULL (situation, médicament)
 */
public record TimeSpec(
    TimeSpan.Precision precision, Instant at, Instant to, String label, boolean ongoing) {

  public static TimeSpec exact(Instant at) {
    return new TimeSpec(TimeSpan.Precision.EXACT, at, null, null, false);
  }

  public static TimeSpec ongoing(TimeSpan.Precision precision, Instant since) {
    return new TimeSpec(precision, since, null, null, true);
  }
}
