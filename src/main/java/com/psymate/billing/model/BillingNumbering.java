package com.psymate.billing.model;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Numérotation des factures et des avoirs (G Q89) : continue, sans trou, par praticien, par nature
 * et par année civile de la date d'émission, au fuseau de {@code Europe/Paris}. Format : préfixe,
 * année, numéro sur quatre chiffres au moins ({@code F-2026-0001}, {@code AV-2026-0001}). Les
 * préfixes viennent des réglages ({@code billing_settings}) et ne changent plus dès qu'un numéro
 * existe ({@code billing_counter}). Chaque année a sa suite : la première facture émise à une date
 * d'une nouvelle année reçoit {@code 0001} ({@link #yearOf}).
 */
public final class BillingNumbering {

  /** Fuseau de l'année civile de numérotation. */
  public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

  /** Nature d'une facture ({@code billing_counter.kind}). */
  public static final String INVOICE = "invoice";

  /** Nature d'un avoir ({@code billing_counter.kind}). */
  public static final String CREDIT_NOTE = "credit_note";

  /** Préfixe par défaut des factures (V18). */
  public static final String DEFAULT_INVOICE_PREFIX = "F";

  /** Préfixe par défaut des avoirs (V18). */
  public static final String DEFAULT_CREDIT_NOTE_PREFIX = "AV";

  private BillingNumbering() {}

  /** Numéro affiché : {@code prefix-année-0001} (quatre chiffres au moins). */
  public static String format(String prefix, int year, int number) {
    return prefix + "-" + year + "-" + String.format(Locale.ROOT, "%04d", number);
  }

  /** Année civile de numérotation du jour, à Paris. */
  public static int currentYear(Clock clock) {
    return LocalDate.now(clock.withZone(ZONE)).getYear();
  }

  /**
   * Année de la suite d'un document : celle de sa date d'émission (G Q89), pas celle du jour où il
   * est émis : une facture datée du 31 décembre et émise le 2 janvier prend la suite de décembre.
   */
  public static int yearOf(LocalDate issueDate) {
    return issueDate.getYear();
  }

  /**
   * Numéro que reçoit le document suivant d'une suite : préfixe, année de sa date d'émission,
   * dernier numéro attribué dans cette année plus un ({@code lastOfThatYear} vaut 0 tant que
   * l'année n'a rien reçu, si bien que la suite repart à {@code 0001}).
   */
  public static String next(String prefix, LocalDate issueDate, int lastOfThatYear) {
    return format(prefix, yearOf(issueDate), Math.addExact(lastOfThatYear, 1));
  }
}
