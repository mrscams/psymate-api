package com.psymate.billing.model;

/**
 * Tarif d'une séance à facturer et son origine (G Q87). Ordre : montant fixé sur la séance ({@code
 * session}), tarif du patient ({@code patient}), prix de la prestation ({@code service} : celle
 * choisie pour la séance, sinon la prestation par défaut) ; sans aucun des trois, {@code none} et
 * pas de montant (la séance ne pourra pas entrer dans une facture : 422 RATE_REQUIRED, M15-08).
 *
 * @param amountCents montant en centimes, null si {@code source} vaut {@code none}
 * @param source session, patient, service ou none
 */
public record BillingRate(Integer amountCents, String source) {

  public static final String SESSION = "session";
  public static final String PATIENT = "patient";
  public static final String SERVICE = "service";
  public static final String NONE = "none";

  /**
   * Tarif retenu selon G Q87 : le premier montant présent (strictement positif) parmi la séance, le
   * patient et la prestation.
   */
  public static BillingRate resolve(
      Integer sessionAmountCents, Integer patientRateCents, Integer servicePriceCents) {
    if (positive(sessionAmountCents)) {
      return new BillingRate(sessionAmountCents, SESSION);
    }
    if (positive(patientRateCents)) {
      return new BillingRate(patientRateCents, PATIENT);
    }
    if (positive(servicePriceCents)) {
      return new BillingRate(servicePriceCents, SERVICE);
    }
    return new BillingRate(null, NONE);
  }

  private static boolean positive(Integer cents) {
    return cents != null && cents > 0;
  }
}
