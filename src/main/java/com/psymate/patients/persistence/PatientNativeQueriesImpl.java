package com.psymate.patients.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import java.util.UUID;

/** Implémentation du fragment Spring Data {@link PatientNativeQueries} (SQL natif f_unaccent). */
class PatientNativeQueriesImpl implements PatientNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  public List<Patient> list(
      UUID tenant,
      UUID owner,
      String pattern,
      boolean archived,
      UUID tagId,
      String orderBy,
      int limit,
      int offset) {
    StringBuilder sql =
        new StringBuilder("select * from patient where tenant_id = :tenant and owner_id = :owner");
    sql.append(archived ? " and archived_at is not null" : " and archived_at is null");
    if (pattern != null) {
      sql.append(" and f_unaccent(display_name) ilike f_unaccent(:pattern)");
    }
    if (tagId != null) {
      sql.append(
          " and exists (select 1 from patient_tag pt where pt.patient_id = patient.id"
              + " and pt.tag_id = :tag)");
    }
    sql.append(" order by ").append(orderBy).append(" limit :limit offset :offset");
    Query query =
        em.createNativeQuery(sql.toString(), Patient.class)
            .setParameter("tenant", tenant)
            .setParameter("owner", owner)
            .setParameter("limit", limit)
            .setParameter("offset", offset);
    if (pattern != null) {
      query.setParameter("pattern", pattern);
    }
    if (tagId != null) {
      query.setParameter("tag", tagId);
    }
    return typed(query.getResultList());
  }

  @Override
  public List<Patient> findOwnedByOrdered(UUID ownerId) {
    return typed(
        em.createNativeQuery(
                "select * from patient where owner_id = :owner"
                    + " order by f_unaccent(lower(display_name)), id",
                Patient.class)
            .setParameter("owner", ownerId)
            .getResultList());
  }

  @SuppressWarnings("unchecked")
  private static List<Patient> typed(List<?> rows) {
    return (List<Patient>) rows;
  }
}
