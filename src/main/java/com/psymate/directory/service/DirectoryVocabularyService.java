package com.psymate.directory.service;

import com.psymate.directory.dto.Item;
import com.psymate.directory.repository.DirectoryVocabularyRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Lecture des entrées actives du vocabulaire de l'annuaire pour {@code GET
 * /api/vocabulary/directory} : le contrôleur ne lit pas le repository lui-même (G Q33).
 */
@Service
public class DirectoryVocabularyService {

  private final DirectoryVocabularyRepository repo;

  DirectoryVocabularyService(DirectoryVocabularyRepository repo) {
    this.repo = repo;
  }

  /** Entrées actives d'un type (specialty ou audience), par rang puis libellé français. */
  public List<Item> activeItems(String kind) {
    return repo.activeItems(kind).stream()
        .map(r -> new Item(r.code(), r.labelFr(), r.labelEn()))
        .toList();
  }
}
