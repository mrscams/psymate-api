package com.psymate.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  private static java.util.List<String> check(String password) {
    return PasswordPolicy.violations(password, "claire@example.org", "Claire", "Lambert");
  }

  @Test
  void elevenCharactersAreRefused() {
    assertThat(check("Motdepasse1")).containsExactly(PasswordPolicy.TOO_SHORT);
    assertThat(check("motdepasse")).containsExactly(PasswordPolicy.TOO_SHORT);
  }

  @Test
  void twelveCharactersWithMixedCaseAreAccepted() {
    assertThat(check("Zebre-Tournesol-42")).isEmpty();
    assertThat(check("Abcdefghijkl")).isEmpty();
  }

  @Test
  void missingCaseIsRefused() {
    assertThat(check("zebre-tournesol-42")).containsExactly(PasswordPolicy.CASE);
    assertThat(check("ZEBRE-TOURNESOL-42")).containsExactly(PasswordPolicy.CASE);
  }

  @Test
  void compromisedPasswordIsRefused() {
    assertThat(check("Password2026!")).contains(PasswordPolicy.COMPROMISED);
  }

  @Test
  void passwordContainingEmailOrNameIsRefused() {
    assertThat(check("Xx-claire-Xx-98765")).contains(PasswordPolicy.PERSONAL);
    assertThat(check("Xx-LAMBERT-Xx-98765")).contains(PasswordPolicy.PERSONAL);
    assertThat(check("Xx-claire@example.org-Q")).contains(PasswordPolicy.PERSONAL);
  }

  @Test
  void nullIsRefused() {
    assertThat(check(null)).containsExactly(PasswordPolicy.TOO_SHORT);
  }

  @Test
  void localListOfCommonPasswordsIsApplied() {
    // « unbelievable » figure dans la liste locale de 10 000 mots de passe courants.
    assertThat(check("Unbelievable")).containsExactly(PasswordPolicy.COMPROMISED);
    assertThat(PasswordPolicy.compromisedCount()).isGreaterThanOrEqualTo(10_000);
  }
}
