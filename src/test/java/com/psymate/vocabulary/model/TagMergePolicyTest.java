package com.psymate.vocabulary.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TagMergePolicyTest {

  @Test
  void rejectsMergeIntoItself() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> TagMergePolicy.check(id, id))
        .isInstanceOfSatisfying(
            VocabularyException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(422);
              assertThat(e.errors()).extracting("field").containsExactly("targetId");
            });
  }

  @Test
  void rejectsMissingTarget() {
    assertThatThrownBy(() -> TagMergePolicy.check(UUID.randomUUID(), null))
        .isInstanceOf(VocabularyException.class);
  }

  @Test
  void acceptsDistinctTags() {
    assertThatCode(() -> TagMergePolicy.check(UUID.randomUUID(), UUID.randomUUID()))
        .doesNotThrowAnyException();
  }
}
