package com.psymate.vocabulary;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Implémentation du fragment Spring Data {@link EmotionNativeQueries} (SQL natif). */
class EmotionNativeQueriesImpl implements EmotionNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  public int rewriteFeelings(String source, String target) {
    if (!NativeSql.tableExists(em, "entry_feeling")) {
      return 0;
    }
    return em.createNativeQuery(
            "update entry_feeling set original_emotion_code = coalesce(original_emotion_code, :s),"
                + " emotion_code = :t where emotion_code = :s")
        .setParameter("s", source)
        .setParameter("t", target)
        .executeUpdate();
  }

  @Override
  public int restoreFeelings(String source, String target) {
    if (!NativeSql.tableExists(em, "entry_feeling")) {
      return 0;
    }
    return em.createNativeQuery(
            "update entry_feeling set emotion_code = :s, original_emotion_code = null"
                + " where original_emotion_code = :s and emotion_code = :t")
        .setParameter("s", source)
        .setParameter("t", target)
        .executeUpdate();
  }
}
