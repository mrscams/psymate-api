package com.psymate.vocabulary;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;

/** Implémentation du fragment Spring Data {@link TagNativeQueries} (SQL natif). */
class TagNativeQueriesImpl implements TagNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  public void addUsageCount(UUID source, UUID target) {
    em.createNativeQuery(
            "update tag set usage_count = usage_count"
                + " + (select usage_count from tag where id = :s) where id = :t")
        .setParameter("s", source)
        .setParameter("t", target)
        .executeUpdate();
    // Le compteur a changé en SQL : l'entité cible déjà chargée doit le relire.
    Tag loaded = em.find(Tag.class, target);
    if (loaded != null) {
      em.refresh(loaded);
    }
  }
}
