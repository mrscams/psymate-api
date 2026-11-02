package psymatefixture.badtables.alpha;

/** Correct : alpha lit et ecrit sa propre table. */
public class AlphaRepository {
  static final String INSERT = "insert into alpha_item (id) values (?)";
  static final String SELECT = "select id from alpha_item" + " where id = ?";
}
