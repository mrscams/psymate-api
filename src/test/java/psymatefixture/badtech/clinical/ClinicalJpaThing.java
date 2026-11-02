package psymatefixture.badtech.clinical;

import jakarta.persistence.EntityManager;

/** Violation : API JPA dans clinical hors de son sous-domaine export (G Q32). */
public class ClinicalJpaThing {
  EntityManager em;
}
