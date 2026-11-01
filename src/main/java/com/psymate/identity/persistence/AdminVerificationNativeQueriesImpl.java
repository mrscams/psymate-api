package com.psymate.identity.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.UUID;

/** Implémentation du fragment Spring Data {@link AdminVerificationNativeQueries}. */
class AdminVerificationNativeQueriesImpl implements AdminVerificationNativeQueries {

  /** Date de soumission : journal de sécurité, il n'y a pas de colonne dédiée. */
  private static final String SUBMITTED_AT =
      "(select max(e.occurred_at) from security_event e where e.practitioner_id = p.id"
          + " and e.kind = 'verification_submitted')";

  @PersistenceContext private EntityManager em;

  /** La direction et l'opérateur viennent de constantes, jamais de l'entrée. */
  @Override
  public List<Tuple> adminQueue(
      String status, boolean oldestFirst, Long cursorKey, UUID cursorId, int limit) {
    String op = oldestFirst ? ">" : "<";
    String dir = oldestFirst ? "asc" : "desc";
    StringBuilder sql = new StringBuilder();
    sql.append(
        "select * from (select p.id, p.display_name, p.profession, p.address_city,"
            + " (select count(*) from verification_document d where d.practitioner_id = p.id"
            + " and d.purpose = 'verification' and d.deleted_at is null) as documents_count,"
            + " coalesce((select string_agg(distinct d.type, ',') from verification_document d"
            + " where d.practitioner_id = p.id and d.purpose = 'verification'"
            + " and d.deleted_at is null and d.purged_at is null), '') as present_types, ");
    sql.append(SUBMITTED_AT).append(" as submitted_at, ");
    sql.append(
        "date_trunc('milliseconds', coalesce("
            + SUBMITTED_AT
            + ", p.updated_at)) as sort_key from practitioner p"
            + " where p.role <> 'admin' and p.verification_status = :status) t");
    if (cursorKey != null) {
      sql.append(
          " where (t.sort_key, t.id) "
              + op
              + " (timestamptz 'epoch' + cast(:key as bigint) * interval '1 millisecond', :cid)");
    }
    sql.append(" order by t.sort_key ").append(dir).append(", t.id ").append(dir);
    sql.append(" limit :limit");
    Query query = em.createNativeQuery(sql.toString(), Tuple.class).setParameter("status", status);
    if (cursorKey != null) {
      query.setParameter("key", cursorKey).setParameter("cid", cursorId);
    }
    return tuples(query.setParameter("limit", limit));
  }

  @Override
  public List<Tuple> adminUnderReview() {
    return tuples(
        em.createNativeQuery(
            "select p.display_name, p.profession, "
                + SUBMITTED_AT
                + " as submitted_at from practitioner p where p.role <> 'admin'"
                + " and p.verification_status = 'under_review' order by submitted_at, p.id",
            Tuple.class));
  }

  @SuppressWarnings("unchecked")
  private static List<Tuple> tuples(Query query) {
    return (List<Tuple>) query.getResultList();
  }
}
