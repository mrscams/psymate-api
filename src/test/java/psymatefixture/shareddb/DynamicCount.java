package psymatefixture.shareddb;

/** Interdit : un nom de table en variable ne prouve rien. */
public class DynamicCount {
  static String sql(String table) {
    return "select count(*) from " + table;
  }
}
