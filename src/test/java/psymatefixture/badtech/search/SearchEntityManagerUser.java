package psymatefixture.badtech.search;

import jakarta.persistence.EntityManager;

/** Violation : API JPA dans un module de projection (G Q32). */
public class SearchEntityManagerUser {
  EntityManager em;
}
