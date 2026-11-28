package com.psymate.billing.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implémentation du fragment Spring Data {@link BillingNativeQueries}. */
class BillingNativeQueriesImpl implements BillingNativeQueries {

  /** Espace de clés des verrous de prestations (préfixe du texte haché). */
  private static final String SERVICES_KEY_PREFIX = "billing.services:";

  /** Espace de clés des verrous de réglages. */
  private static final String SETTINGS_KEY_PREFIX = "billing.settings:";

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockServices(UUID practitionerId) {
    lock(SERVICES_KEY_PREFIX + practitionerId);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockSettings(UUID practitionerId) {
    lock(SETTINGS_KEY_PREFIX + practitionerId);
  }

  private void lock(String key) {
    // pg_advisory_xact_lock renvoie void : appelée dans FROM, la requête rend une ligne lisible.
    em.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(:key, 0))")
        .setParameter("key", key)
        .getSingleResult();
  }
}
