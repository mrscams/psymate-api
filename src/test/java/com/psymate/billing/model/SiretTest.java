package com.psymate.billing.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Clé de Luhn du SIRET (M15-06, L5.1), sans base. */
class SiretTest {

  @Test
  void validSiretsPassTheLuhnKey() {
    assertThat(Siret.isValid("73282932000074")).isTrue();
    assertThat(Siret.isValid("44306184100047")).isTrue();
  }

  @Test
  void aWrongCheckDigitIsRefused() {
    assertThat(Siret.isValid("73282932000075")).isFalse();
    assertThat(Siret.isValid("12345678901234")).isFalse();
  }

  @Test
  void anythingButFourteenDigitsIsRefused() {
    assertThat(Siret.isValid("7328293200007")).isFalse();
    assertThat(Siret.isValid("732829320000740")).isFalse();
    assertThat(Siret.isValid("7328293200007A")).isFalse();
    assertThat(Siret.isValid("")).isFalse();
    assertThat(Siret.isValid(null)).isFalse();
  }

  @Test
  void spacesAreRemovedBeforeTheCheck() {
    assertThat(Siret.normalize(" 732 829 320 00074 ")).isEqualTo("73282932000074");
    assertThat(Siret.isValid(Siret.normalize("732 829 320 00074"))).isTrue();
    assertThat(Siret.normalize(null)).isNull();
  }

  @Test
  void luhnDoublesEverySecondDigitFromTheRight() {
    assertThat(Siret.luhn("79927398713")).isTrue();
    assertThat(Siret.luhn("79927398710")).isFalse();
    assertThat(Siret.luhn("0")).isTrue();
  }
}
