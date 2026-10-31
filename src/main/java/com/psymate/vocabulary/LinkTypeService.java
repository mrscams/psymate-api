package com.psymate.vocabulary;

import com.psymate.vocabulary.api.LinkTypeCatalog;
import com.psymate.vocabulary.api.LinkTypeView;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
class LinkTypeService implements LinkTypeCatalog {

  private final VocabularyRepository repo;

  LinkTypeService(VocabularyRepository repo) {
    this.repo = repo;
  }

  @Override
  public List<LinkTypeView> activeLinkTypes() {
    return repo.linkTypes();
  }

  @Override
  public Optional<LinkTypeView> findByCode(String code) {
    return repo.linkTypes().stream().filter(t -> t.code().equals(code)).findFirst();
  }
}
