package psymatefixture.shareddb;

/** Correct : la prose « delete from tenant » d'un commentaire n'est pas du SQL. */
public class Comments {
  // select count(*) from tenant
  /* truncate table tenant */
  static final char QUOTE = '"';
  static final String SQL = "select count(*) from tenant where id = ?";
}
