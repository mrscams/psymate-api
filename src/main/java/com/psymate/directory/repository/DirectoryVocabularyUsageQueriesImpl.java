package com.psymate.directory.repository;

import com.psymate.directory.model.DirectoryVocabularyId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Implémentation du fragment Spring Data {@link DirectoryVocabularyUsageQueries}. */
class DirectoryVocabularyUsageQueriesImpl implements DirectoryVocabularyUsageQueries {

  private static final String USED_BY =
      " from directory_vocabulary v join practitioner_profile p on p.published and"
          + " case v.kind when 'specialty' then v.code = any(p.specialties)"
          + " else v.code = any(p.audiences) end";

  @PersistenceContext private EntityManager em;

  @Override
  public Map<DirectoryVocabularyId, Integer> usedByPublishedProfiles(String kind) {
    String sql =
        "select v.kind, v.code, count(*)"
            + USED_BY
            + (kind == null ? "" : " where v.kind = :kind")
            + " group by v.kind, v.code";
    Query query = em.createNativeQuery(sql);
    if (kind != null) {
      query.setParameter("kind", kind);
    }
    Map<DirectoryVocabularyId, Integer> used = new HashMap<>();
    for (Object row : query.getResultList()) {
      Object[] cols = (Object[]) row;
      used.put(
          new DirectoryVocabularyId((String) cols[0], (String) cols[1]),
          ((Number) cols[2]).intValue());
    }
    return used;
  }

  @Override
  public int usedByPublishedProfiles(String kind, String code) {
    List<?> rows =
        em.createNativeQuery(
                "select count(*)" + USED_BY + " where v.kind = :kind and v.code = :code")
            .setParameter("kind", kind)
            .setParameter("code", code)
            .getResultList();
    return ((Number) rows.get(0)).intValue();
  }
}
