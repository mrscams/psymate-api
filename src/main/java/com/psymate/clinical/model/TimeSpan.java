package com.psymate.clinical.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * Plage d'occurrence d'une entrée (A4.2). Toutes les bornes sont en UTC.
 *
 * @param from occurred_from, jamais nul
 * @param to occurred_to, nul = en cours
 */
public record TimeSpan(Instant from, Instant to, Precision precision, String label) {

  /** Les six précisions ; {@link #code()} est la valeur stockée dans occurred_precision. */
  public enum Precision {
    EXACT("exact"),
    DAY("day"),
    MONTH("month"),
    YEAR("year"),
    PERIOD("period"),
    LIFE_PHASE("life_phase");

    private final String code;

    Precision(String code) {
      this.code = code;
    }

    public String code() {
      return code;
    }

    public static Precision fromCode(String code) {
      for (Precision p : values()) {
        if (p.code.equals(code)) {
          return p;
        }
      }
      throw new IllegalArgumentException("Précision inconnue : " + code);
    }
  }

  private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

  public TimeSpan {
    if (from == null) {
      throw invalid("occurred_from est obligatoire");
    }
    if (to != null && to.isBefore(from)) {
      throw invalid("occurred_to ne peut pas précéder occurred_from");
    }
  }

  /**
   * Résout une saisie en bornes (A4.2).
   *
   * @param birthDate date de naissance du patient, nécessaire pour life_phase seulement
   */
  public static TimeSpan resolve(TimeSpec spec, LocalDate birthDate) {
    Precision p = spec.precision() == null ? Precision.EXACT : spec.precision();
    if (p == Precision.LIFE_PHASE) {
      return lifePhase(spec.label(), birthDate);
    }
    if (spec.at() == null) {
      throw invalid("La date de référence est obligatoire");
    }
    LocalDate day = spec.at().atZone(ZoneOffset.UTC).toLocalDate();
    switch (p) {
      case DAY:
        return bounded(day, day, spec, p);
      case MONTH:
        YearMonth ym = YearMonth.from(day);
        return bounded(ym.atDay(1), ym.atEndOfMonth(), spec, p);
      case YEAR:
        return bounded(
            LocalDate.of(day.getYear(), 1, 1), LocalDate.of(day.getYear(), 12, 31), spec, p);
      case PERIOD:
        if (spec.label() == null || spec.label().isBlank()) {
          throw invalid("Une période exige un libellé");
        }
        if (spec.to() == null && !spec.ongoing()) {
          throw invalid("Une période exige une fin (ou ongoing)");
        }
        return new TimeSpan(spec.at(), spec.ongoing() ? null : spec.to(), p, spec.label());
      default:
        Instant to = spec.ongoing() ? null : (spec.to() != null ? spec.to() : spec.at());
        return new TimeSpan(spec.at(), to, Precision.EXACT, spec.label());
    }
  }

  private static TimeSpan bounded(LocalDate first, LocalDate last, TimeSpec spec, Precision p) {
    Instant from = first.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to = spec.ongoing() ? null : last.atTime(END_OF_DAY).toInstant(ZoneOffset.UTC);
    return new TimeSpan(from, to, p, spec.label());
  }

  private static TimeSpan lifePhase(String label, LocalDate birthDate) {
    LifePhase phase =
        LifePhase.fromLabel(label).orElseThrow(() -> invalid("Phase de vie inconnue : " + label));
    if (birthDate == null) {
      throw invalid("Une phase de vie exige la date de naissance du patient");
    }
    Instant from = birthDate.plusYears(phase.fromYears()).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to = birthDate.plusYears(phase.toYears()).atStartOfDay(ZoneOffset.UTC).toInstant();
    return new TimeSpan(from, to, Precision.LIFE_PHASE, label);
  }

  private static ClinicalException invalid(String message) {
    return new ClinicalException(ClinicalError.INVALID_TIME, message);
  }
}
