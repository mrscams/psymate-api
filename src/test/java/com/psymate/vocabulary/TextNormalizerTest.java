package com.psymate.vocabulary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TextNormalizerTest {

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of("anxiété", "anxiete"),
        Arguments.of("Anxiete", "anxiete"),
        Arguments.of("#anxiété", "anxiete"),
        Arguments.of("  # Anxiété  ", "anxiete"),
        Arguments.of("ÉCOLE", "ecole"),
        Arguments.of("Mélancolie", "melancolie"),
        Arguments.of("Dégoût", "degout"),
        Arguments.of("Culpabilité", "culpabilite"),
        Arguments.of("ça va", "ca va"),
        Arguments.of("naïveté", "naivete"),
        Arguments.of("Ça   va", "ca   va"),
        Arguments.of("##deux", "deux"),
        Arguments.of("Œuvre", "oeuvre"),
        Arguments.of("Cœur", "coeur"),
        Arguments.of("   ", ""),
        Arguments.of("#", ""),
        Arguments.of(null, ""));
  }

  @ParameterizedTest
  @MethodSource("cases")
  void normalizes(String input, String expected) {
    assertThat(TextNormalizer.normalize(input)).isEqualTo(expected);
  }
}
