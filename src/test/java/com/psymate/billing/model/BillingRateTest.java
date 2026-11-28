package com.psymate.billing.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Calcul du tarif d'une séance à facturer (G Q87) : les quatre sources, dans l'ordre. */
class BillingRateTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-28T10:00:00Z");
  private static final UUID PRACTITIONER = UUID.randomUUID();
  private static final UUID TENANT = UUID.randomUUID();

  @Test
  void theAmountFixedOnTheSessionComesFirst() {
    assertThat(BillingRate.resolve(9000, 5500, 6000))
        .isEqualTo(new BillingRate(9000, BillingRate.SESSION));
  }

  @Test
  void withoutSessionAmountThePatientRateComesNext() {
    assertThat(BillingRate.resolve(null, 5500, 6000))
        .isEqualTo(new BillingRate(5500, BillingRate.PATIENT));
  }

  @Test
  void withoutSessionAmountNorPatientRateTheServicePriceApplies() {
    assertThat(BillingRate.resolve(null, null, 6000))
        .isEqualTo(new BillingRate(6000, BillingRate.SERVICE));
  }

  @Test
  void withNothingTheRateIsNoneWithoutAmount() {
    assertThat(BillingRate.resolve(null, null, null))
        .isEqualTo(new BillingRate(null, BillingRate.NONE));
  }

  @Test
  void aZeroOrNegativeAmountIsIgnored() {
    assertThat(BillingRate.resolve(0, -1, 6000)).isEqualTo(new BillingRate(6000, "service"));
  }

  @Test
  void pricingUsesTheChosenServiceOtherwiseTheDefaultOne() {
    BillingService standard = service("Séance individuelle", 6000, true);
    BillingService couple = service("Séance de couple", 9000, false);
    UUID patient = UUID.randomUUID();
    UUID ratedPatient = UUID.randomUUID();
    BillingPricing pricing =
        new BillingPricing(
            Map.of(ratedPatient, 5500),
            Map.of(standard.getId(), standard, couple.getId(), couple),
            standard);

    BillableSession plain = session(patient, null, null);
    assertThat(pricing.serviceOf(plain)).isSameAs(standard);
    assertThat(pricing.rateOf(plain)).isEqualTo(new BillingRate(6000, "service"));

    BillableSession ofCouple = session(patient, couple.getId(), null);
    assertThat(pricing.serviceOf(ofCouple)).isSameAs(couple);
    assertThat(pricing.rateOf(ofCouple)).isEqualTo(new BillingRate(9000, "service"));

    // G Q87 : le tarif du patient passe avant le prix de la prestation, même choisie.
    assertThat(pricing.rateOf(session(ratedPatient, couple.getId(), null)))
        .isEqualTo(new BillingRate(5500, "patient"));
    assertThat(pricing.rateOf(session(ratedPatient, null, 7000)))
        .isEqualTo(new BillingRate(7000, "session"));

    assertThat(pricing.baseRateOf(ratedPatient)).isEqualTo(new BillingRate(5500, "patient"));
    assertThat(pricing.baseRateOf(patient)).isEqualTo(new BillingRate(6000, "service"));
    assertThat(new BillingPricing(Map.of(), Map.of(), null).baseRateOf(patient))
        .isEqualTo(new BillingRate(null, "none"));
  }

  private static BillingService service(String label, int price, boolean isDefault) {
    return new BillingService(
        UUID.randomUUID(), TENANT, PRACTITIONER, label, 50, price, isDefault, NOW);
  }

  private static BillableSession session(UUID patient, UUID serviceId, Integer amount) {
    BillableSession s = org.springframework.beans.BeanUtils.instantiateClass(BillableSession.class);
    ReflectionTestUtils.setField(s, "sessionEntryId", UUID.randomUUID());
    ReflectionTestUtils.setField(s, "patientId", patient);
    ReflectionTestUtils.setField(s, "state", BillableSession.PENDING);
    ReflectionTestUtils.setField(s, "serviceId", serviceId);
    ReflectionTestUtils.setField(s, "amountCents", amount);
    return s;
  }
}
