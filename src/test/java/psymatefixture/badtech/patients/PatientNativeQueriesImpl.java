package psymatefixture.badtech.patients;

import jakarta.persistence.EntityManager;

/** Correct : le SQL natif d'un module JPA passe par un fragment et l'EntityManager (G Q38). */
public class PatientNativeQueriesImpl {
  EntityManager em;
}
