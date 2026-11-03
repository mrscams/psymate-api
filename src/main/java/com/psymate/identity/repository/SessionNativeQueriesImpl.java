package com.psymate.identity.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Implémentation du fragment Spring Data {@link SessionNativeQueries}. */
class SessionNativeQueriesImpl implements SessionNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional
  public int deleteSessions(UUID practitionerId) {
    return em.createNativeQuery("delete from spring_session where principal_name = :p")
        .setParameter("p", practitionerId.toString())
        .executeUpdate();
  }

  @Override
  @Transactional
  public int deleteOtherSessions(UUID practitionerId, String currentSessionId) {
    return em.createNativeQuery(
            "delete from spring_session where principal_name = :p and session_id <> :s")
        .setParameter("p", practitionerId.toString())
        .setParameter("s", currentSessionId)
        .executeUpdate();
  }

  @Override
  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public List<ActiveSessionRow> findActiveSessions(UUID practitionerId, Instant now) {
    List<Object[]> rows =
        em.createNativeQuery(
                "select primary_id, session_id, creation_time, last_access_time, expiry_time"
                    + " from spring_session where principal_name = :p and expiry_time > :now"
                    + " order by last_access_time desc, creation_time desc")
            .setParameter("p", practitionerId.toString())
            .setParameter("now", now.toEpochMilli())
            .getResultList();
    return rows.stream()
        .map(
            r ->
                new ActiveSessionRow(
                    ((String) r[0]).trim(),
                    ((String) r[1]).trim(),
                    Instant.ofEpochMilli(((Number) r[2]).longValue()),
                    Instant.ofEpochMilli(((Number) r[3]).longValue()),
                    Instant.ofEpochMilli(((Number) r[4]).longValue())))
        .toList();
  }

  @Override
  @Transactional
  public int deleteSessionByPrimaryId(UUID practitionerId, String primaryId) {
    return em.createNativeQuery(
            "delete from spring_session where principal_name = :p and primary_id = :id")
        .setParameter("p", practitionerId.toString())
        .setParameter("id", primaryId)
        .executeUpdate();
  }
}
