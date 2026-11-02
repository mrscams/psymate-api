package com.psymate.vocabulary.service;

import com.psymate.vocabulary.api.EntryTypeView;
import com.psymate.vocabulary.repository.EntryTypeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lecture du catalogue des types d'entrées (endpoint GET /api/vocabulary/entry-types). */
@Service
public class EntryTypeService {

  private final EntryTypeRepository entryTypes;

  public EntryTypeService(EntryTypeRepository entryTypes) {
    this.entryTypes = entryTypes;
  }

  @Transactional(readOnly = true)
  public List<EntryTypeView> activeEntryTypes() {
    return entryTypes.findByActiveTrueOrderBySortOrder().stream()
        .map(VocabularyViews::entryType)
        .toList();
  }
}
