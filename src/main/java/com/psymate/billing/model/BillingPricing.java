package com.psymate.billing.model;

import java.util.Map;
import java.util.UUID;

/**
 * Ce qu'il faut savoir pour chiffrer des séances à facturer (G Q87) : le tarif de chaque patient,
 * les prestations du praticien (archivées comprises, une séance peut garder une prestation archivée
 * depuis) et sa prestation par défaut.
 *
 * <p>Prestation retenue d'une séance : celle choisie pour la séance, sinon la prestation par
 * défaut. Tarif : {@link BillingRate#resolve} avec le montant de la séance, le tarif du patient et
 * le prix de la prestation retenue.
 */
public final class BillingPricing {

  private final Map<UUID, Integer> patientRates;
  private final Map<UUID, BillingService> services;
  private final BillingService defaultService;

  /**
   * @param patientRates tarif en centimes par patient ; un patient sans tarif est absent
   * @param services prestations du praticien par identifiant
   * @param defaultService prestation par défaut, null s'il n'y en a pas
   */
  public BillingPricing(
      Map<UUID, Integer> patientRates,
      Map<UUID, BillingService> services,
      BillingService defaultService) {
    this.patientRates = Map.copyOf(patientRates);
    this.services = Map.copyOf(services);
    this.defaultService = defaultService;
  }

  /** Prestation retenue : celle de la séance, sinon la prestation par défaut ; null sinon. */
  public BillingService serviceOf(BillableSession session) {
    BillingService chosen =
        session.getServiceId() == null ? null : services.get(session.getServiceId());
    return chosen != null ? chosen : defaultService;
  }

  /** Tarif de la séance et son origine (G Q87). */
  public BillingRate rateOf(BillableSession session) {
    BillingService service = serviceOf(session);
    return BillingRate.resolve(
        session.getAmountCents(),
        patientRates.get(session.getPatientId()),
        service == null ? null : service.getPriceCents());
  }

  /**
   * Tarif commun d'un patient, sans montant fixé ni prestation choisie sur une séance : son tarif,
   * sinon le prix de la prestation par défaut.
   */
  public BillingRate baseRateOf(UUID patientId) {
    return BillingRate.resolve(
        null,
        patientRates.get(patientId),
        defaultService == null ? null : defaultService.getPriceCents());
  }
}
