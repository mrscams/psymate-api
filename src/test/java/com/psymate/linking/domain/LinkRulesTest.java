package com.psymate.linking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.vocabulary.api.LinkTypeView;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Règles pures de M2-01 sur la matrice réelle de V02__vocabulary_seed.sql (E5.3, G Q12). */
class LinkRulesTest {

  private static final List<String> ALL = List.of("note", "event", "situation", "feeling");

  private static LinkTypeView type(
      String code, boolean directed, List<String> sources, List<String> targets, int order) {
    return new LinkTypeView(
        code,
        directed,
        code,
        code,
        directed ? code + "-inv" : null,
        null,
        sources,
        targets,
        "solid",
        order);
  }

  /** Les huit types de V02, dans l'ordre du seed. */
  private static final List<LinkTypeView> CATALOG =
      List.of(
          type(
              "triggers",
              true,
              List.of("event", "situation", "session"),
              List.of("feeling", "situation", "event"),
              1),
          type(
              "caused_by",
              true,
              List.of("feeling", "situation", "event"),
              List.of("event", "situation"),
              2),
          type(
              "worsens",
              true,
              List.of("situation", "event", "medication"),
              List.of("feeling", "situation"),
              3),
          type(
              "relieves",
              true,
              List.of("event", "situation", "medication", "session"),
              List.of("feeling", "situation"),
              4),
          type(
              "co_occurs",
              false,
              List.of("note", "event", "situation", "feeling", "medication"),
              List.of("note", "event", "situation", "feeling", "medication"),
              5),
          type("follows", true, ALL, ALL, 6),
          type(
              "part_of",
              true,
              List.of("event", "feeling", "note"),
              List.of("situation", "session"),
              7),
          type("treats", true, List.of("medication"), List.of("situation", "feeling"), 8));

  private final UUID a = UUID.randomUUID();
  private final UUID b = UUID.randomUUID();

  static Stream<Arguments> matrix() {
    return Stream.of(
        // lien, type source, type cible, autorisé ?
        Arguments.of("triggers", "note", "feeling", false),
        Arguments.of("triggers", "event", "feeling", true),
        Arguments.of("triggers", "situation", "feeling", true),
        Arguments.of("triggers", "session", "situation", true),
        Arguments.of("triggers", "feeling", "event", false),
        Arguments.of("triggers", "medication", "feeling", false),
        Arguments.of("caused_by", "feeling", "event", true),
        Arguments.of("caused_by", "note", "event", false),
        Arguments.of("caused_by", "feeling", "feeling", false),
        Arguments.of("worsens", "medication", "feeling", true),
        Arguments.of("worsens", "note", "feeling", false),
        Arguments.of("relieves", "session", "feeling", true),
        Arguments.of("relieves", "feeling", "event", false),
        Arguments.of("co_occurs", "note", "medication", true),
        Arguments.of("co_occurs", "session", "note", false),
        Arguments.of("follows", "note", "feeling", true),
        Arguments.of("follows", "medication", "note", false),
        Arguments.of("part_of", "note", "session", true),
        Arguments.of("part_of", "event", "feeling", false),
        Arguments.of("treats", "medication", "situation", true),
        Arguments.of("treats", "note", "feeling", false),
        Arguments.of("treats", "medication", "note", false));
  }

  @ParameterizedTest(name = "{0} : {1} vers {2} autorisé = {3}")
  @MethodSource("matrix")
  void matrixDecidesFromSourceAndTargetTypes(
      String linkType, String sourceType, String targetType, boolean allowed) {
    if (allowed) {
      assertThat(LinkRules.validate(a, b, sourceType, targetType, linkType, CATALOG).code())
          .isEqualTo(linkType);
    } else {
      assertThatThrownBy(() -> LinkRules.validate(a, b, sourceType, targetType, linkType, CATALOG))
          .isInstanceOfSatisfying(
              LinkException.class,
              e -> assertThat(e.error()).isEqualTo(LinkError.LINK_TYPE_NOT_ALLOWED));
    }
  }

  @Test
  void noteToFeelingTriggersIsRefusedAndTheErrorListsWhatIsPossible() {
    assertThatThrownBy(() -> LinkRules.validate(a, b, "note", "feeling", "triggers", CATALOG))
        .isInstanceOfSatisfying(
            LinkException.class,
            e ->
                assertThat(e.allowedTypes())
                    .extracting(LinkTypeView::code)
                    .containsExactly("co_occurs", "follows"));
  }

  @Test
  void eventToFeelingTriggersIsAllowed() {
    assertThat(LinkRules.allows(CATALOG.get(0), "event", "feeling")).isTrue();
  }

  @Test
  void aSessionToASessionHasNoPossibleType() {
    assertThat(LinkRules.allowedTypes(CATALOG, "session", "session")).isEmpty();
  }

  @Test
  void selfLinkIsRefusedBeforeAnythingElse() {
    assertThatThrownBy(() -> LinkRules.validate(a, a, "event", "event", "unknown", CATALOG))
        .isInstanceOfSatisfying(
            LinkException.class, e -> assertThat(e.error()).isEqualTo(LinkError.SELF_LINK));
  }

  @Test
  void unknownTypeIsRefused() {
    assertThatThrownBy(() -> LinkRules.validate(a, b, "event", "feeling", "causes", CATALOG))
        .isInstanceOfSatisfying(
            LinkException.class, e -> assertThat(e.error()).isEqualTo(LinkError.UNKNOWN_LINK_TYPE));
  }

  @Test
  void aTypeAbsentFromTheActiveCatalogIsUnknown() {
    List<LinkTypeView> withoutTriggers = CATALOG.subList(1, CATALOG.size());
    assertThatThrownBy(
            () -> LinkRules.validate(a, b, "event", "feeling", "triggers", withoutTriggers))
        .isInstanceOfSatisfying(
            LinkException.class, e -> assertThat(e.error()).isEqualTo(LinkError.UNKNOWN_LINK_TYPE));
  }

  @Test
  void errorCodesCarryTheirHttpStatus() {
    assertThat(LinkError.SELF_LINK.status()).isEqualTo(422);
    assertThat(LinkError.DIFFERENT_PATIENT.status()).isEqualTo(422);
    assertThat(LinkError.UNKNOWN_LINK_TYPE.status()).isEqualTo(422);
    assertThat(LinkError.LINK_TYPE_NOT_ALLOWED.status()).isEqualTo(422);
    assertThat(LinkError.LINK_EXISTS.status()).isEqualTo(409);
  }
}
