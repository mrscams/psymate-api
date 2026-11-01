package com.psymate.vocabulary;

import com.psymate.vocabulary.api.EntryTypeView;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lecture du catalogue des types d'entrées (endpoint GET /api/vocabulary/entry-types). */
@Service
class EntryTypeService {

  private final EntryTypeRepository entryTypes;

  EntryTypeService(EntryTypeRepository entryTypes) {
    this.entryTypes = entryTypes;
  }

  @Transactional(readOnly = true)
  List<EntryTypeView> activeEntryTypes() {
    return entryTypes.findByActiveTrueOrderBySortOrder().stream()
        .map(VocabularyViews::entryType)
        .toList();
  }
}
