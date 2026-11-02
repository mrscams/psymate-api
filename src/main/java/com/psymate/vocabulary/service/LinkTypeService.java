package com.psymate.vocabulary.service;

import com.psymate.vocabulary.api.LinkTypeCatalog;
import com.psymate.vocabulary.api.LinkTypeView;
import com.psymate.vocabulary.repository.LinkTypeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkTypeService implements LinkTypeCatalog {

  private final LinkTypeRepository linkTypes;

  public LinkTypeService(LinkTypeRepository linkTypes) {
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
