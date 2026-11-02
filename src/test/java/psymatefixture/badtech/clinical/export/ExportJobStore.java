package psymatefixture.badtech.clinical.export;

import jakarta.persistence.EntityManager;

/** Correct : clinical.export est un sous-domaine JPA dans un module JdbcClient. */
public class ExportJobStore {
  EntityManager em;
}
