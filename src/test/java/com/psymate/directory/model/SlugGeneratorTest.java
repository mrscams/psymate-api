package com.psymate.directory.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

  @Test
  void normalizesAccentsCaseAndPunctuation() {
    assertThat(SlugGenerator.normalize("Hélène  Dupont-Œuvre")).isEqualTo("helene-dupont-oeuvre");
  }

  @Test
  void dropsLeadingTitle() {
    assertThat(SlugGenerator.normalize("Dr Maya Kaddour")).isEqualTo("maya-kaddour");
  }

  @Test
  void fallsBackWhenNothingUsable() {
    assertThat(SlugGenerator.normalize("???")).isEqualTo("praticien");
    assertThat(SlugGenerator.normalize(null)).isEqualTo("praticien");
  }

  @Test
  void addsNumericSuffixOnCollision() {
    Set<String> taken = Set.of("claire-lambert", "claire-lambert-2");
    assertThat(SlugGenerator.unique("Claire Lambert", s -> false)).isEqualTo("claire-lambert");
    assertThat(SlugGenerator.unique("Claire Lambert", Set.of("claire-lambert")::contains))
        .isEqualTo("claire-lambert-2");
    assertThat(SlugGenerator.unique("Claire Lambert", taken::contains))
        .isEqualTo("claire-lambert-3");
  }
}
