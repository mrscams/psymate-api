package com.psymate.vocabulary;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;

/** Implémentation du fragment Spring Data {@link TagNativeQueries} (SQL natif). */
class TagNativeQueriesImpl implements TagNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  public void moveTagUsages(UUID source, UUID target) {
    if (NativeSql.tableExists(em, "entry_tag")) {
      em.createNativeQuery(
              "delete from entry_tag where tag_id = :s and entry_id in"
                  + " (select entry_id from entry_tag where tag_id = :t)")
          .setParameter("s", source)
          .setParameter("t", target)
          .executeUpdate();
      em.createNativeQuery("update entry_tag set tag_id = :t where tag_id = :s")
          .setParameter("s", source)
          .setParameter("t", target)
          .executeUpdate();
    }
    em.createNativeQuery(
            "delete from patient_tag where tag_id = :s and patient_id in"
                + " (select patient_id from patient_tag where tag_id = :t)")
        .setParameter("s", source)
        .setParameter("t", target)
        .executeUpdate();
    em.createNativeQuery("update patient_tag set tag_id = :t where tag_id = :s")
        .setParameter("s", source)
        .setParameter("t", target)
        .executeUpdate();
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
