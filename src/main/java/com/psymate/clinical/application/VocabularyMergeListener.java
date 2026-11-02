package com.psymate.clinical.application;

import com.psymate.events.api.EventDeduplicator;
import com.psymate.vocabulary.api.EmotionMergeReverted;
import com.psymate.vocabulary.api.EmotionsMerged;
import com.psymate.vocabulary.api.TagsMerged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Réécrit {@code entry_feeling} et {@code entry_tag} après une fusion du vocabulaire (M9-03, revue
 * ARCH-01). Traitement hors requête, donc sans praticien connecté : le praticien vient de
 * l'événement. Idempotent de deux façons : EventDeduplicator.once écarte un rejeu de la même
 * publication, et {@link EntryRelabelService} ne touche que les lignes pas encore dans l'état
 * voulu, relu dans vocabulary.
 */
@Component
class VocabularyMergeListener {

  static final String EMOTIONS_MERGED = "clinical.emotions-merged";
  static final String EMOTION_MERGE_REVERTED = "clinical.emotion-merge-reverted";
  static final String TAGS_MERGED = "clinical.tags-merged";

  private final EntryRelabelService relabel;
  private final EventDeduplicator deduplicator;

  VocabularyMergeListener(EntryRelabelService relabel, EventDeduplicator deduplicator) {
    this.relabel = relabel;
    this.deduplicator = deduplicator;
  }

  @ApplicationModuleListener(id = EMOTIONS_MERGED)
  void on(EmotionsMerged event) {
    deduplicator.once(
        event,
        EMOTIONS_MERGED,
        () ->
            relabel.relabelFeelings(
                event.getTenantId(), event.getPractitionerId(), event.getSourceCode()));
  }

  @ApplicationModuleListener(id = EMOTION_MERGE_REVERTED)
  void on(EmotionMergeReverted event) {
    deduplicator.once(
        event,
        EMOTION_MERGE_REVERTED,
        () ->
            relabel.relabelFeelings(
                event.getTenantId(), event.getPractitionerId(), event.getSourceCode()));
  }

  @ApplicationModuleListener(id = TAGS_MERGED)
  void on(TagsMerged event) {
    deduplicator.once(
        event,
        TAGS_MERGED,
        () -> relabel.relabelTags(event.getTenantId(), event.getPractitionerId()));
  }
}
