package psymatefixture.badtables.beta;

/** Correct : beta_link est dans la liste E9 de la fixture. */
public class BetaLinkPurge {
  static final String SQL = "delete from beta_link where id = ?";
}
