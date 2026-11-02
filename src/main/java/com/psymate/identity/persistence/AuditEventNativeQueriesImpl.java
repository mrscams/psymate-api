package com.psymate.identity.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.query.NativeQuery;
import org.springframework.transaction.annotation.Transactional;

/** Implémentation du fragment Spring Data {@link AuditEventNativeQueries}. */
class AuditEventNativeQueriesImpl implements AuditEventNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional
  public void insertAuditEvent(
      UUID id,
      UUID practitionerId,
      String kind,
      String ip,
      String userAgent,
      String detailsJson,
      OffsetDateTime occurredAt) {
    NativeQuery<?> insert =
        em.createNativeQuery(
                "insert into security_event (id, practitioner_id, actor_id, kind, ip, user_agent,"
                    + " details, occurred_at) values (:id, :p, null, :k, cast(:ip as inet), :ua,"
                    + " cast(:details as jsonb), :at)")
            .unwrap(NativeQuery.class);
    // Types explicites : un paramètre null sans type ne serait lié ni en varchar ni en uuid.
    insert
        .setParameter("id", id, UUID.class)
        .setParameter("p", practitionerId, UUID.class)
        .setParameter("k", kind, String.class)
        .setParameter("ip", ip, String.class)
        .setParameter("ua", userAgent, String.class)
        .setParameter("details", detailsJson, String.class)
        .setParameter("at", occurredAt, OffsetDateTime.class)
        .executeUpdate();
  }
}
