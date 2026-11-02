package com.psymate.identity.verification.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** G Q14 : diplôme et pièce d'identité pour toutes les professions ; le reste est conseillé. */
class CompletenessRulesTest {

  static Stream<Arguments> cases() {
    return Stream.of(
        // profession, documents déposés, manquants, recommandés
        Arguments.of(
            "psychologist", List.of(), List.of("diploma", "identity"), List.of("registration")),
        Arguments.of(
            "psychologist", List.of("diploma"), List.of("identity"), List.of("registration")),
        Arguments.of(
            "psychologist", List.of("identity"), List.of("diploma"), List.of("registration")),
        Arguments.of(
            "psychologist", List.of("diploma", "identity"), List.of(), List.of("registration")),
        Arguments.of(
            "psychologist", List.of("diploma", "identity", "registration"), List.of(), List.of()),
        Arguments.of(
            "psychiatrist", List.of(), List.of("diploma", "identity"), List.of("registration")),
        Arguments.of(
            "psychiatrist", List.of("diploma"), List.of("identity"), List.of("registration")),
        Arguments.of(
            "psychiatrist", List.of("diploma", "identity"), List.of(), List.of("registration")),
        Arguments.of(
            "psychiatrist", List.of("diploma", "identity", "registration"), List.of(), List.of()),
        Arguments.of("coach", List.of(), List.of("diploma", "identity"), List.of("certification")),
        Arguments.of("coach", List.of("diploma"), List.of("identity"), List.of("certification")),
        Arguments.of("coach", List.of("diploma", "identity"), List.of(), List.of("certification")),
        Arguments.of(
            "coach", List.of("diploma", "identity", "certification"), List.of(), List.of()),
        // les types « autres » ne comptent pas
        Arguments.of(
            "coach",
            List.of("other", "professional_card"),
            List.of("diploma", "identity"),
            List.of("certification")));
  }

  @ParameterizedTest
  @MethodSource("cases")
  void missingAndRecommendedByProfession(
      String profession, List<String> present, List<String> missing, List<String> recommended) {
    assertThat(CompletenessRules.missing(profession, present)).isEqualTo(missing);
    assertThat(CompletenessRules.recommended(profession, present)).isEqualTo(recommended);
  }

  @ParameterizedTest
  @MethodSource("cases")
  void recommendedNeverBlocks(
      String profession, List<String> present, List<String> missing, List<String> recommended) {
    assertThat(CompletenessRules.missing(profession, present)).doesNotContain("registration");
    assertThat(CompletenessRules.missing(profession, present)).doesNotContain("certification");
  }
}
