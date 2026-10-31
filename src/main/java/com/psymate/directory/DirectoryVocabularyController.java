package com.psymate.directory;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Listes fermées de l'annuaire (G Q15) : spécialités et publics en base, modalités et langues. */
@RestController
@RequestMapping("/api/vocabulary/directory")
class DirectoryVocabularyController {

  record Item(String code, String labelFr, String labelEn) {}

  record DirectoryVocabularyDto(
      List<Item> specialties, List<Item> audiences, List<Item> modalities, List<Item> languages) {}

  private static final List<Item> MODALITIES =
      List.of(
          new Item("in_person", "Au cabinet", "In person"),
          new Item("video", "En visio", "Video"),
          new Item("home", "À domicile", "At home"));

  /** Langues proposées, codes ISO 639-1 (tout code ISO 639-1 reste accepté en écriture). */
  private static final List<String> LANGUAGES =
      List.of(
          "fr", "en", "es", "de", "it", "pt", "ar", "nl", "ru", "pl", "tr", "zh", "ja", "ro", "hi");

  private final ProfileRepository repo;

  DirectoryVocabularyController(ProfileRepository repo) {
    this.repo = repo;
  }

  @GetMapping
  DirectoryVocabularyDto get() {
    List<Item> languages =
        LANGUAGES.stream()
            .map(
                c -> {
                  Locale l = Locale.forLanguageTag(c);
                  return new Item(
                      c, l.getDisplayLanguage(Locale.FRENCH), l.getDisplayLanguage(Locale.ENGLISH));
                })
            .sorted(Comparator.comparing(Item::labelFr))
            .toList();
    return new DirectoryVocabularyDto(items("specialty"), items("audience"), MODALITIES, languages);
  }

  private List<Item> items(String kind) {
    return repo.activeVocabulary(kind).stream()
        .map(r -> new Item(r.code(), r.labelFr(), r.labelEn()))
        .toList();
  }
}
