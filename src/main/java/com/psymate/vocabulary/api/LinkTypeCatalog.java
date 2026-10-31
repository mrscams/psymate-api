package com.psymate.vocabulary.api;

import java.util.List;
import java.util.Optional;

/** Façade des types de liens et de leur matrice source/cible. */
public interface LinkTypeCatalog {

  List<LinkTypeView> activeLinkTypes();

  Optional<LinkTypeView> findByCode(String code);
}
