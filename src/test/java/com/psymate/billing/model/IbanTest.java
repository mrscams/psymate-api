package com.psymate.billing.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Format et clé mod 97 de l'IBAN (M15-06, G Q95), sans base. */
class IbanTest {

  @Test
  void validIbansPassTheMod97Key() {
    assertThat(Iban.isValid("FR7630006000011234567890189")).isTrue();
    assertThat(Iban.isValid("DE89370400440532013000")).isTrue();
    assertThat(Iban.isValid("GB82WEST12345698765432")).isTrue();
    assertThat(Iban.isValid("NO9386011117947")).isTrue();
  }

  @Test
  void aWrongCheckIsRefused() {
    assertThat(Iban.isValid("FR7630006000011234567890188")).isFalse();
    assertThat(Iban.isValid("FR7730006000011234567890189")).isFalse();
    assertThat(Iban.mod97("FR7630006000011234567890188")).isEqualTo(71);
    assertThat(Iban.mod97("FR7630006000011234567890189")).isEqualTo(1);
  }

  @Test
  void aWrongFormatIsRefused() {
    assertThat(Iban.isValid("7630006000011234567890189")).isFalse();
    assertThat(Iban.isValid("FRXX30006000011234567890189")).isFalse();
    assertThat(Iban.isValid("FR76300060")).isFalse();
    assertThat(Iban.isValid("FR76" + "1".repeat(31))).isFalse();
    assertThat(Iban.isValid("FR76-3000-6000-0112-3456-7890-189")).isFalse();
    assertThat(Iban.isValid("")).isFalse();
    assertThat(Iban.isValid(null)).isFalse();
  }

  @Test
  void spacesAndCaseAreNormalizedBeforeTheCheck() {
    String typed = " fr76 3000 6000 0112 3456 7890 189 ";
    assertThat(Iban.normalize(typed)).isEqualTo("FR7630006000011234567890189");
    assertThat(Iban.isValid(Iban.normalize(typed))).isTrue();
    assertThat(Iban.isValid(typed)).isFalse();
    assertThat(Iban.normalize(null)).isNull();
  }

  @Test
  void onlyTheLastFourCharactersAreKept() {
    assertThat(Iban.last4("FR7630006000011234567890189")).isEqualTo("0189");
  }
}
