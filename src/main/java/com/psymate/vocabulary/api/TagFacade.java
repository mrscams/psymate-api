package com.psymate.vocabulary.api;

import java.util.List;
import java.util.Optional;

/** Façade des tags du praticien courant. */
public interface TagFacade {

  /** Tags actifs (non archivés) du praticien courant. */
  List<TagView> tagsOfCurrentPractitioner();

  /** Résout un tag actif par libellé, après normalisation (casse, accents, '#'). */
  Optional<TagView> resolve(String label);
}
