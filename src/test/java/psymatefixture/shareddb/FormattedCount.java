package psymatefixture.shareddb;

/** Interdit : le nom de table formaté ne prouve rien. */
public class FormattedCount {
  static String sql(String table) {
    return "select count(*) from %s".formatted(table);
  }
}
