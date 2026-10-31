package com.psymate.vocabulary;

import java.util.UUID;

/** Règles pures de fusion de tags (testables sans base). */
final class TagMergePolicy {

  private TagMergePolicy() {}

  static void check(UUID sourceId, UUID targetId) {
    if (targetId == null) {
      throw VocabularyException.invalid("targetId", "La cible est obligatoire.");
    }
    if (sourceId.equals(targetId)) {
      throw VocabularyException.invalid("targetId", "La cible doit différer de la source.");
    }
  }
}
