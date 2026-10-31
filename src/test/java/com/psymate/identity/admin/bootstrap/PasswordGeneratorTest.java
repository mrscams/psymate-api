package com.psymate.identity.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Mot de passe à usage unique : longueur, alphabet, casse mixte, imprévisibilité. */
class PasswordGeneratorTest {

  private final PasswordGenerator generator = new PasswordGenerator();

  @Test
  void hasTwentyCharactersFromLettersAndDigitsOnly() {
    for (int i = 0; i < 200; i++) {
      String p = generator.generate();
      assertThat(p).hasSize(20).matches("[A-Za-z0-9]{20}");
    }
  }

  @Test
  void alwaysContainsUpperAndLowerCase() {
    for (int i = 0; i < 500; i++) {
      String p = generator.generate();
      assertThat(p).matches(".*[A-Z].*").matches(".*[a-z].*");
      // Règle de PasswordPolicy (identity.auth, non accessible d'ici) : au moins 12 caractères.
      assertThat(p.length()).isGreaterThanOrEqualTo(12);
    }
  }

  @Test
  void drawsDiffer() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      seen.add(generator.generate());
    }
    assertThat(seen).hasSize(100);
  }
}
