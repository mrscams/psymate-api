package com.psymate.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Recherche de la liste des factures : texte normalisé, motif échappé. */
class InvoiceListServiceTest {

  @Test
  void searchIgnoresCaseAndAccents() {
    assertThat(InvoiceListService.normalize("Hélène ÉLOÏSE")).isEqualTo("helene eloise");
    assertThat(InvoiceListService.normalize("F-2026-0042")).isEqualTo("f-2026-0042");
  }

  @Test
  void likeWildcardsAreEscaped() {
    assertThat(InvoiceListService.containsPattern("ro")).isEqualTo("%ro%");
    assertThat(InvoiceListService.containsPattern("50%_!")).isEqualTo("%50!%!_!!%");
  }
}
