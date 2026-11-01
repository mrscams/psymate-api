package com.psymate.vocabulary;

import com.psymate.vocabulary.api.LinkTypeCatalog;
import com.psymate.vocabulary.api.LinkTypeView;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LinkTypeService implements LinkTypeCatalog {

  private final LinkTypeRepository linkTypes;

  LinkTypeService(LinkTypeRepository linkTypes) {
    this.linkTypes = linkTypes;
  }

  @Override
  @Transactional(readOnly = true)
  public List<LinkTypeView> activeLinkTypes() {
    return linkTypes.findByActiveTrueOrderBySortOrder().stream()
        .map(VocabularyViews::linkType)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LinkTypeView> findByCode(String code) {
    return linkTypes.findByCodeAndActiveTrue(code).map(VocabularyViews::linkType);
  }
}
