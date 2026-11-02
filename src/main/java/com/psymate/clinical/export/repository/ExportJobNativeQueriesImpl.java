package com.psymate.clinical.export.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;

/** Implémentation du fragment Spring Data {@link ExportJobNativeQueries}. */
class ExportJobNativeQueriesImpl implements ExportJobNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  public void lockPractitioner(UUID practitionerId) {
    em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:k, 0))")
        .setParameter("k", "export:" + practitionerId)
        .getResultList();
  }
}
