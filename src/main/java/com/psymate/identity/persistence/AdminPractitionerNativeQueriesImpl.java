package com.psymate.identity.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Implémentation du fragment Spring Data {@link AdminPractitionerNativeQueries}. */
class AdminPractitionerNativeQueriesImpl implements AdminPractitionerNativeQueries {

  private static final String SUBMITTED_AT =
      "(select max(e.occurred_at) from security_event e where e.practitioner_id = p.id"
          + " and e.kind = 'verification_submitted')";

  private static final String COLUMNS =
      "p.id, p.display_name, p.email, p.profession, p.address_city, p.role,"
          + " p.verification_status, p.verified_at, "
          + SUBMITTED_AT
          + " as submitted_at, p.updated_at, (p.totp_enabled_at is not null) as totp,"
          + " p.created_at, p.last_login_at, p.suspended_at, p.purge_after,"
          + " date_trunc('milliseconds', p.created_at) as sort_key";

  /** Caractère d'échappement des ILIKE de la recherche. */
  private static final String ESC = "!";

  @PersistenceContext private EntityManager em;

  /** Échappe le caractère d'échappement, % et _ pour un ILIKE. */
  private static String like(String q) {
    return "%" + q.replace(ESC, ESC + ESC).replace("%", ESC + "%").replace("_", ESC + "_") + "%";
  }

  private static String where(
      String q, String status, String role, String profession, Map<String, Object> params) {
    StringBuilder sql = new StringBuilder(" where true");
    if (q != null) {
      sql.append(
          " and (p.display_name ilike :q escape '"
              + ESC
              + "' or p.email ilike :q escape '"
              + ESC
              + "')");
      params.put("q", like(q));
    }
    if (status != null) {
      sql.append(" and p.verification_status = :status");
      params.put("status", status);
    }
    if (role != null) {
      sql.append(" and p.role = :role");
      params.put("role", role);
    }
    if (profession != null) {
      sql.append(" and p.profession = :profession");
      params.put("profession", profession);
    }
    return sql.toString();
  }

  private static Query bind(Query query, Map<String, Object> params) {
    params.forEach(query::setParameter);
    return query;
  }

  @Override
  public List<Tuple> adminAccounts(
      String q,
      String status,
      String role,
      String profession,
      Long cursorKey,
      UUID cursorId,
      int limit) {
    Map<String, Object> params = new HashMap<>();
    StringBuilder sql =
        new StringBuilder(
            "select "
                + COLUMNS
                + " from practitioner p"
                + where(q, status, role, profession, params));
    if (cursorKey != null) {
      sql.append(
          " and (date_trunc('milliseconds', p.created_at), p.id) <"
              + " (timestamptz 'epoch' + cast(:key as bigint) * interval '1 millisecond', :cid)");
      params.put("key", cursorKey);
      params.put("cid", cursorId);
    }
    sql.append(" order by date_trunc('milliseconds', p.created_at) desc, p.id desc limit :limit");
    params.put("limit", limit);
    return tuples(bind(em.createNativeQuery(sql.toString(), Tuple.class), params));
  }

  @Override
  public Map<String, Integer> adminStatusCounts(String q, String role, String profession) {
    Map<String, Object> params = new HashMap<>();
    String sql =
        "select p.verification_status as s, count(*) as n from practitioner p"
            + where(q, null, role, profession, params)
            + " group by p.verification_status";
    Map<String, Integer> found = new HashMap<>();
    for (Tuple row : tuples(bind(em.createNativeQuery(sql, Tuple.class), params))) {
      found.put(Rows.string(row, "s"), Rows.integer(row, "n"));
    }
    return found;
  }

  @Override
  public Optional<Tuple> adminAccount(UUID id) {
    return tuples(
            em.createNativeQuery(
                    "select " + COLUMNS + " from practitioner p where p.id = :id", Tuple.class)
                .setParameter("id", id))
        .stream()
        .findFirst();
  }

  @Override
  public Optional<String> previousStatusBeforeSuspension(UUID practitionerId) {
    List<?> rows =
        em.createNativeQuery(
                "select checklist->>'previousStatus' from verification_decision"
                    + " where practitioner_id = :p and kind = 'suspended'"
                    + " order by decided_at desc, id desc limit 1")
            .setParameter("p", practitionerId)
            .getResultList();
    return rows.isEmpty() ? Optional.empty() : Optional.ofNullable((String) rows.get(0));
  }

  @SuppressWarnings("unchecked")
  private static List<Tuple> tuples(Query query) {
    return (List<Tuple>) query.getResultList();
  }
}
