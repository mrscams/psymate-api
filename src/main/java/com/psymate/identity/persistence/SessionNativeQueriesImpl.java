package com.psymate.identity.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
}
