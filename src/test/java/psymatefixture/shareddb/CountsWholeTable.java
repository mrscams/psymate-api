package psymatefixture.shareddb;

/** Interdit : compte toute la table, un autre test y écrit peut-être. */
public class CountsWholeTable {
  static final String SQL = "select count(*) from tenant";
  static final String ONE = "select count(1) from patient";
}
