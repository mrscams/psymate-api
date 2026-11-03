package psymatefixture.shareddb;

/** Correct : test à base dédiée (liste fermée de la fixture). */
public class DedicatedBase {
  static final String SQL = "select count(*) from practitioner";
  static final String CLEAN = "delete from practitioner";
}
