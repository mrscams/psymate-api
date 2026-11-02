package psymatefixture.badshared.shared.ratelimit;

import jakarta.persistence.EntityManager;

/** Violation : shared utilise l'EntityManager. */
public class BadEntityManagerUser {
  EntityManager em;
}
