package psymatefixture.badtables.alpha;

/** Violation : lecture d'une table de beta, separee par un echappement \n. */
public class AlphaEscapedRead {
  static final String SQL = "select 1\nfrom beta_item";
}
