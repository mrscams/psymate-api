package com.psymate.identity.repository;

import com.psymate.shared.time.Timestamps;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Implémentation du fragment Spring Data {@link AdminJournalNativeQueries}. */
class AdminJournalNativeQueriesImpl implements AdminJournalNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  public List<Tuple> adminJournal(
      String kind,
      UUID practitionerId,
      UUID actorId,
      Instant from,
      Instant to,
      Long cursorKey,
      UUID cursorId,
      int limit) {
    Map<String, Object> params = new HashMap<>();
    StringBuilder sql =
        new StringBuilder(
            "select j.id, j.occurred_at, j.kind, j.source, j.actor_id, a.display_name as actor_name,"
                + " j.practitioner_id, p.display_name as practitioner_name, j.summary,"
                + " cast(j.details as text) as details from admin_journal_v j"
                + " left join practitioner a on a.id = j.actor_id"
                + " left join practitioner p on p.id = j.practitioner_id"
                + " where j.kind not like 'patient.%'");
    if (kind != null) {
      sql.append(" and (j.kind = :kind or j.kind like :kindPrefix)");
      params.put("kind", kind);
      params.put("kindPrefix", kind.replace("_", "\\_") + ".%");
    }
    if (practitionerId != null) {
      sql.append(" and j.practitioner_id = :practitioner");
      params.put("practitioner", practitionerId);
    }
    if (actorId != null) {
      sql.append(" and j.actor_id = :actor");
      params.put("actor", actorId);
    }
    if (from != null) {
      sql.append(" and j.occurred_at >= :from");
      params.put("from", Timestamps.utc(from));
    }
    if (to != null) {
      sql.append(" and j.occurred_at <= :to");
      params.put("to", Timestamps.utc(to));
    }
    if (cursorKey != null) {
      sql.append(
          " and (date_trunc('milliseconds', j.occurred_at), j.id) <"
              + " (timestamptz 'epoch' + cast(:key as bigint) * interval '1 millisecond', :cid)");
      params.put("key", cursorKey);
      params.put("cid", cursorId);
    }
    sql.append(" order by date_trunc('milliseconds', j.occurred_at) desc, j.id desc limit :limit");
    params.put("limit", limit);
    Query query = em.createNativeQuery(sql.toString(), Tuple.class);
    params.forEach(query::setParameter);
    return tuples(query);
  }

  @SuppressWarnings("unchecked")
  private static List<Tuple> tuples(Query query) {
    return (List<Tuple>) query.getResultList();
  }
}
