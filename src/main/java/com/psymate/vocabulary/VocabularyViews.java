package com.psymate.vocabulary;

import com.psymate.vocabulary.api.EmotionAffect;
import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.EntryTypeView;
import com.psymate.vocabulary.api.LinkTypeView;
import com.psymate.vocabulary.api.TagView;

/**
 * Conversions entité vers record de l'API : les entités ne sortent jamais des services du module.
 */
final class VocabularyViews {

  private VocabularyViews() {}

  static EmotionView emotion(Emotion e) {
    return new EmotionView(
        e.getCode(),
        e.getScope(),
        e.getFamily(),
        e.getIntensityLevel(),
        e.getLabelFr(),
        e.getLabelEn(),
        e.isActive());
  }

  static EmotionAffect affect(Emotion e) {
    return new EmotionAffect(e.getCode(), e.getValence(), e.getArousal());
  }

  static TagView tag(Tag t) {
    return new TagView(
        t.getId(), t.getLabel(), t.getNormalizedLabel(), t.getUsageCount(), t.getCreatedAt());
  }

  static EntryTypeView entryType(EntryType t) {
    return new EntryTypeView(
        t.getCode(),
        t.getLabelFr(),
        t.getLabelEn(),
        t.getDetailTable(),
        t.hasDuration(),
        t.getSortOrder());
  }

  static LinkTypeView linkType(LinkType t) {
    return new LinkTypeView(
        t.getCode(),
        t.isDirected(),
        t.getLabelFr(),
        t.getLabelEn(),
        t.getInverseLabelFr(),
        t.getInverseLabelEn(),
        t.getAllowedSourceTypes(),
        t.getAllowedTargetTypes(),
        t.getLineStyle(),
        t.getSortOrder());
  }
}
