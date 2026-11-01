package com.psymate.identity.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implémentation du fragment Spring Data {@link AppealNativeQueries}. */
class AppealNativeQueriesImpl implements AppealNativeQueries {

  private static final String SELECT =
      "select a.id, a.practitioner_id, p.display_name, a.decision_id, d.kind as decision_kind,"
          + " d.admin_id as decision_admin_id, a.message, a.submitted_at, a.reviewed_at,"
          + " a.reviewed_by, a.outcome, a.response_message,"
          + " date_trunc('milliseconds', a.submitted_at) as sort_key"
          + " from practitioner_appeal a join verification_decision d on d.id = a.decision_id"
          + " join practitioner p on p.id = a.practitioner_id";

  @PersistenceContext private EntityManager em;

  @Override
  public List<Tuple> appealPage(String status, Long cursorKey, UUID cursorId, int limit) {
    boolean oldestFirst = "open".equals(status);
    StringBuilder sql = new StringBuilder(SELECT).append(" where true");
    if ("open".equals(status)) {
      sql.append(" and a.reviewed_at is null");
    } else if ("decided".equals(status)) {
      sql.append(" and a.reviewed_at is not null");
    }
    if (cursorKey != null) {
      sql.append(" and (date_trunc('milliseconds', a.submitted_at), a.id) ")
          .append(oldestFirst ? ">" : "<")
          .append(" (timestamptz 'epoch' + cast(:key as bigint) * interval '1 millisecond', :cid)");
    }
    String dir = oldestFirst ? "asc" : "desc";
    sql.append(" order by date_trunc('milliseconds', a.submitted_at) ")
        .append(dir)
        .append(", a.id ")
        .append(dir)
        .append(" limit :limit");
    Query query = em.createNativeQuery(sql.toString(), Tuple.class);
    if (cursorKey != null) {
      query.setParameter("key", cursorKey).setParameter("cid", cursorId);
    }
    return tuples(query.setParameter("limit", limit));
  }

  @Override
  public Optional<Tuple> appealRow(UUID id) {
    return tuples(
            em.createNativeQuery(SELECT + " where a.id = :id", Tuple.class).setParameter("id", id))
        .stream()
        .findFirst();
  }

  @SuppressWarnings("unchecked")
  private static List<Tuple> tuples(Query query) {
    return (List<Tuple>) query.getResultList();
  }
}
