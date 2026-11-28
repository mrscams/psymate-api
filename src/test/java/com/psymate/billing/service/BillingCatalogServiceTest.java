package com.psymate.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Règles du défaut et de la limite des prestations (M15-05, L5.1), sans base. */
class BillingCatalogServiceTest {

  @Test
  void firstServiceIsAlwaysTheDefault() {
    assertThat(BillingCatalogService.createdAsDefault(null, 0)).isTrue();
    assertThat(BillingCatalogService.createdAsDefault(false, 0)).isTrue();
    assertThat(BillingCatalogService.createdAsDefault(true, 0)).isTrue();
  }

  @Test
  void laterServiceIsTheDefaultOnlyWhenAsked() {
    assertThat(BillingCatalogService.createdAsDefault(null, 1)).isFalse();
    assertThat(BillingCatalogService.createdAsDefault(false, 3)).isFalse();
    assertThat(BillingCatalogService.createdAsDefault(true, 3)).isTrue();
  }

  @Test
  void fiftyActiveServicesAtMost() {
    assertThat(BillingCatalogService.belowLimit(0)).isTrue();
    assertThat(BillingCatalogService.belowLimit(49)).isTrue();
    assertThat(BillingCatalogService.belowLimit(50)).isFalse();
    assertThat(BillingCatalogService.belowLimit(51)).isFalse();
  }

  @Test
  void theDefaultIsArchivableOnlyWhenItIsTheLastActiveService() {
    assertThat(BillingCatalogService.archivable(true, 1)).isTrue();
    assertThat(BillingCatalogService.archivable(true, 2)).isFalse();
    assertThat(BillingCatalogService.archivable(false, 2)).isTrue();
    assertThat(BillingCatalogService.archivable(false, 1)).isTrue();
  }

  @Test
  void theDefaultLosesItsRoleOnlyToAnotherService() {
    assertThat(BillingCatalogService.defaultFlagAccepted(true, false)).isFalse();
    assertThat(BillingCatalogService.defaultFlagAccepted(true, true)).isTrue();
    assertThat(BillingCatalogService.defaultFlagAccepted(true, null)).isTrue();
    assertThat(BillingCatalogService.defaultFlagAccepted(false, false)).isTrue();
    assertThat(BillingCatalogService.defaultFlagAccepted(false, true)).isTrue();
  }
}
