package psymatefixture.badtables.alpha;

/** Violation : alpha ecrit une table de beta. */
public class AlphaWritesBeta {
  static final String SQL = "update beta_item b set name = ? where b.id = ?";
}
