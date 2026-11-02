package com.psymate.directory.repository;

import com.psymate.directory.model.DirectoryVocabularyId;
import java.util.Map;

/**
 * Nombre de fiches publiées qui utilisent chaque code du vocabulaire. Natif : {@code v.code =
 * any(p.specialties)} (opérateur de tableau PostgreSQL) n'a pas d'équivalent JPQL.
 */
interface DirectoryVocabularyUsageQueries {

  /**
   * Comptes de toutes les entrées utilisées (type donné, ou les deux si {@code kind} est null) en
   * UNE requête groupée ; une entrée absente de la map n'est utilisée par aucune fiche publiée.
   */
  Map<DirectoryVocabularyId, Integer> usedByPublishedProfiles(String kind);

  /** Nombre de fiches publiées qui utilisent ce code. */
  int usedByPublishedProfiles(String kind, String code);
}
