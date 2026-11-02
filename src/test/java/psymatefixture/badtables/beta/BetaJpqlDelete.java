package psymatefixture.badtables.beta;

/** Violation : suppression physique (JPQL, nom d'entite) d'une table hors E9. */
public class BetaJpqlDelete {
  static final String JPQL = "delete from BetaItem b where b.id = :id";
}
