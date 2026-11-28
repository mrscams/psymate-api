package com.psymate.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Désignation d'une ligne de séance : en français, sans texte clinique (G Q83). */
class BillingTextsTest {

  private final BillingTexts texts = new BillingTexts();

  @Test
  void aSessionLineIsNamedAfterItsDayOrItsService() {
    LocalDate day = LocalDate.of(2026, 11, 5);
    assertThat(texts.sessionLineLabel(null, day)).isEqualTo("Séance du 05/11/2026");
    assertThat(texts.sessionLineLabel(" ", day)).isEqualTo("Séance du 05/11/2026");
    assertThat(texts.sessionLineLabel("Séance de couple", day))
        .isEqualTo("Séance de couple du 05/11/2026");
    assertThat(texts.sessionLineLabel("Bilan {date}", day)).isEqualTo("Bilan {date} du 05/11/2026");
  }
}
