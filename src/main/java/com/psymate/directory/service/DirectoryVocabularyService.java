package com.psymate.directory.service;

import com.psymate.directory.dto.DirectoryVocabularyDto;
import com.psymate.directory.dto.Item;
import com.psymate.directory.repository.DirectoryVocabularyRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Lecture du vocabulaire de l'annuaire pour {@code GET /api/vocabulary/directory} (praticien) et
 * {@code GET /api/public/directory/vocabulary} (public), qui rendent le même contenu : les
 * contrôleurs ne lisent pas le repository eux-mêmes (G Q33).
 */
@Service
public class DirectoryVocabularyService {

  private static final List<Item> MODALITIES =
      List.of(
          new Item("in_person", "Au cabinet", "In person"),
          new Item("video", "En visio", "Video"),
          new Item("home", "À domicile", "At home"));

  /** Langues proposées, codes ISO 639-1 (tout code ISO 639-1 reste accepté en écriture). */
  private static final List<String> LANGUAGES =
      List.of(
          "fr", "en", "es", "de", "it", "pt", "ar", "nl", "ru", "pl", "tr", "zh", "ja", "ro", "hi");

  private final DirectoryVocabularyRepository repo;

  DirectoryVocabularyService(DirectoryVocabularyRepository repo) {
    this.repo = repo;
  }

  /**
   * Les quatre listes fermées de l'annuaire : spécialités et publics actifs (base), modalités et
   * langues (constantes). Listes complètes, non paginées.
   */
  public DirectoryVocabularyDto directoryVocabulary() {
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
    return new DirectoryVocabularyDto(
        activeItems("specialty"), activeItems("audience"), MODALITIES, languages);
  }

  /** Entrées actives d'un type (specialty ou audience), par rang puis libellé français. */
  public List<Item> activeItems(String kind) {
    return repo.activeItems(kind).stream()
        .map(r -> new Item(r.code(), r.labelFr(), r.labelEn()))
        .toList();
  }
}
