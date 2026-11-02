package psymatefixture.badtables.search;

/** Correct : une projection lit les tables des autres modules (G Q34). */
public class SearchReadsBeta {
  static final String SQL = "select b.name from beta_item b, alpha_item a";
}
