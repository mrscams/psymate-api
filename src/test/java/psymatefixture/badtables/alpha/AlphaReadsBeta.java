package psymatefixture.badtables.alpha;

/** Violation : alpha n'est pas une projection et lit une table de beta (requete concatenee). */
public class AlphaReadsBeta {
  static final String SQL =
      "select a.id from alpha_item a" + " join" + " beta_item b on b.alpha_id = a.id";
}
