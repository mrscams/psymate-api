package com.psymate.agenda.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implémentation du fragment Spring Data {@link BookingNativeQueries}. */
class BookingNativeQueriesImpl implements BookingNativeQueries {

  /** Espace de clés des verrous de praticien (préfixe du texte haché). */
  private static final String KEY_PREFIX = "agenda.booking:";

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockPractitioner(UUID practitionerId) {
    // pg_advisory_xact_lock renvoie void : appelée dans FROM, la requête rend une ligne lisible.
    em.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(:key, 0))")
        .setParameter("key", KEY_PREFIX + practitionerId)
        .getSingleResult();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockEmail(String emailHash) {
    // Espace de clés distinct de celui du praticien (agenda.booking:{uuid}).
    em.createNativeQuery(
            "select 1 from pg_advisory_xact_lock(hashtextextended('agenda.booking.email:' ||"
                + " :hash, 0))")
        .setParameter("hash", emailHash)
        .getSingleResult();
  }
}
