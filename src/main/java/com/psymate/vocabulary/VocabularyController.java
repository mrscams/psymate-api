package com.psymate.vocabulary;

import com.psymate.vocabulary.api.EmotionCatalog;
import com.psymate.vocabulary.api.EmotionView;
import com.psymate.vocabulary.api.EntryTypeView;
import com.psymate.vocabulary.api.LinkTypeCatalog;
import com.psymate.vocabulary.api.LinkTypeView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lecture des vocabulaires. Sera protégé par la sécurité de M0-04 (pas encore présente). */
@RestController
@RequestMapping("/api/vocabulary")
class VocabularyController {

  private final EmotionCatalog emotions;
  private final LinkTypeCatalog linkTypes;
  private final VocabularyRepository repo;

  VocabularyController(
      EmotionCatalog emotions, LinkTypeCatalog linkTypes, VocabularyRepository repo) {
    this.emotions = emotions;
    this.linkTypes = linkTypes;
    this.repo = repo;
  }

  @GetMapping("/emotions")
  List<EmotionView> emotions() {
    return emotions.activeEmotions();
  }

  @GetMapping("/link-types")
  List<LinkTypeView> linkTypes() {
    return linkTypes.activeLinkTypes();
  }

  @GetMapping("/entry-types")
  List<EntryTypeView> entryTypes() {
    return repo.entryTypes();
  }
}
