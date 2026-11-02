package com.psymate.identity.repository;

import com.psymate.identity.model.Practitioner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Implémentation du fragment Spring Data {@link PractitionerNativeQueries}. */
class PractitionerNativeQueriesImpl implements PractitionerNativeQueries {

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional
  public void clearMustChangePassword(UUID practitionerId) {
    em.flush();
    em.createNativeQuery(
            "update practitioner set preferences = preferences - cast(:key as text) where id = :id")
        .setParameter("key", Practitioner.MUST_CHANGE_PASSWORD)
        .setParameter("id", practitionerId)
        .executeUpdate();
    // Le document a changé en SQL : l'entité déjà chargée dans cette transaction doit le relire.
    Practitioner loaded = em.find(Practitioner.class, practitionerId);
    if (loaded != null) {
      em.refresh(loaded);
    }
  }
}
